package org.specdriven.skill.discovery;

import org.specdriven.agent.permission.PermissionContext;
import org.specdriven.skill.hotload.SkillHotLoadPermissionException;
import org.specdriven.skill.hotload.SkillHotLoadTrustException;
import org.specdriven.skill.hotload.SkillHotLoader;
import org.specdriven.skill.hotload.SkillHotLoaderException;
import org.specdriven.skill.hotload.SkillLoadResult;
import org.specdriven.skill.sql.SkillMarkdownParser;
import org.specdriven.skill.sql.SkillMarkdownParser.ParsedSkill;
import org.specdriven.skill.sql.SkillSqlConverter;
import org.specdriven.skill.sql.SkillSqlException;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;

/**
 * Scans a skills directory for SKILL.md files, generates CREATE SERVICE DDL,
 * and registers each skill via Lealone JDBC.
 */
public final class SkillAutoDiscovery {

    private static final String EXECUTOR_PACKAGE = "org.specdriven.skill.executor";
    private static final PermissionContext HOT_LOAD_PERMISSION_CONTEXT =
            new PermissionContext("skill-auto-discovery", "hot-load", "skill-auto-discovery");

    private final String jdbcUrl;
    private final Path skillsDir;
    private final SkillHotLoader hotLoader;

    public SkillAutoDiscovery(String jdbcUrl, Path skillsDir) {
        this(jdbcUrl, skillsDir, null);
    }

    public SkillAutoDiscovery(String jdbcUrl, Path skillsDir, SkillHotLoader hotLoader) {
        this.jdbcUrl = Objects.requireNonNull(jdbcUrl, "jdbcUrl must not be null");
        this.skillsDir = Objects.requireNonNull(skillsDir, "skillsDir must not be null");
        this.hotLoader = hotLoader;
    }

    /**
     * Scan skillsDir, parse each SKILL.md, and execute CREATE SERVICE DDL.
     *
     * @return summary of registered and failed skills
     * @throws SkillSqlException if skillsDir cannot be listed
     */
    public DiscoveryResult discoverAndRegister() {
        List<Path> skillFiles = findSkillFiles();

        int registered = 0;
        int failed = 0;
        int hotLoaded = 0;
        int hotLoadFailed = 0;
        List<SkillDiscoveryError> errors = new ArrayList<>();

        for (Path skillMd : skillFiles) {
            try {
                ParsedSkill parsed = SkillMarkdownParser.parse(skillMd);
                HotLoadOutcome hotLoadOutcome = hotLoadSkill(skillMd, parsed);
                if (hotLoadOutcome != null) {
                    if (hotLoadOutcome.success()) {
                        hotLoaded++;
                    } else {
                        hotLoadFailed++;
                        errors.add(new SkillDiscoveryError(hotLoadOutcome.path(), hotLoadOutcome.errorMessage()));
                    }
                }
                registerSkill(skillMd, parsed);
                registered++;
            } catch (SkillSqlException | SQLException e) {
                failed++;
                errors.add(new SkillDiscoveryError(skillMd, e.getMessage()));
            }
        }

        return new DiscoveryResult(registered, failed, hotLoaded, hotLoadFailed, errors);
    }

    private List<Path> findSkillFiles() {
        try (Stream<Path> entries = Files.list(skillsDir)) {
            return entries
                    .filter(Files::isDirectory)
                    .map(dir -> dir.resolve("SKILL.md"))
                    .filter(Files::exists)
                    .toList();
        } catch (IOException e) {
            throw new SkillSqlException("Cannot list skills directory: " + skillsDir.toAbsolutePath(), e);
        }
    }

    private void registerSkill(Path skillMd, ParsedSkill parsed) throws SQLException {
        String sql = SkillSqlConverter.convert(parsed.frontmatter(), skillMd.getParent());

        try (Connection conn = DriverManager.getConnection(jdbcUrl);
             Statement stmt = conn.createStatement()) {
            stmt.executeUpdate(sql);
        }
    }

    private HotLoadOutcome hotLoadSkill(Path skillMd, ParsedSkill parsed) {
        if (hotLoader == null) {
            return null;
        }

        Path skillDir = skillMd.getParent();
        String skillName = parsed.frontmatter().name();
        String entryClassName = executorClassName(skillName);
        Path javaSourcePath = skillDir.resolve(simpleExecutorClassName(skillName) + ".java");
        if (!Files.isRegularFile(javaSourcePath)) {
            return null;
        }

        try {
            String javaSource = Files.readString(javaSourcePath);
            String sourceHash = sha256(javaSource);
            SkillLoadResult result = hotLoader.load(
                    skillName, entryClassName, javaSource, sourceHash, HOT_LOAD_PERMISSION_CONTEXT);
            if (result.success()) {
                return HotLoadOutcome.success(javaSourcePath);
            }
            return HotLoadOutcome.failure(javaSourcePath, diagnosticMessage(result));
        } catch (IOException | SkillHotLoaderException | SkillHotLoadPermissionException | SkillHotLoadTrustException e) {
            String message = e.getMessage() != null ? e.getMessage() : e.getClass().getName();
            return HotLoadOutcome.failure(javaSourcePath, message);
        }
    }

    private static String diagnosticMessage(SkillLoadResult result) {
        if (result.diagnostics().isEmpty()) {
            return "Hot-load failed for executor class " + result.entryClassName();
        }
        return result.diagnostics().stream()
                .map(diagnostic -> diagnostic.message())
                .filter(message -> message != null && !message.isBlank())
                .findFirst()
                .orElse("Hot-load failed for executor class " + result.entryClassName());
    }

    private static String executorClassName(String skillName) {
        return EXECUTOR_PACKAGE + "." + simpleExecutorClassName(skillName);
    }

    private static String simpleExecutorClassName(String skillName) {
        StringBuilder sb = new StringBuilder();
        for (String part : skillName.split("-")) {
            if (!part.isEmpty()) {
                sb.append(Character.toUpperCase(part.charAt(0)));
                if (part.length() > 1) {
                    sb.append(part.substring(1));
                }
            }
        }
        return sb.append("Executor").toString();
    }

    private static String sha256(String source) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(source.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is unavailable", e);
        }
    }

    private record HotLoadOutcome(boolean success, Path path, String errorMessage) {

        private static HotLoadOutcome success(Path path) {
            return new HotLoadOutcome(true, path, null);
        }

        private static HotLoadOutcome failure(Path path, String errorMessage) {
            return new HotLoadOutcome(false, path, errorMessage);
        }
    }
}
