package org.specdriven.skill.discovery;

import org.specdriven.skill.sql.SkillMarkdownParser;
import org.specdriven.skill.sql.SkillMarkdownParser.ParsedSkill;
import org.specdriven.skill.sql.SkillSqlConverter;
import org.specdriven.skill.sql.SkillSqlException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Stream;

/**
 * Scans a skills directory for SKILL.md files, generates CREATE SERVICE DDL,
 * and registers each skill via Lealone JDBC.
 */
public final class SkillAutoDiscovery {

    private final String jdbcUrl;
    private final Path skillsDir;

    public SkillAutoDiscovery(String jdbcUrl, Path skillsDir) {
        this.jdbcUrl = jdbcUrl;
        this.skillsDir = skillsDir;
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
        List<SkillDiscoveryError> errors = new ArrayList<>();

        for (Path skillMd : skillFiles) {
            try {
                registerSkill(skillMd);
                registered++;
            } catch (SkillSqlException | SQLException e) {
                failed++;
                errors.add(new SkillDiscoveryError(skillMd, e.getMessage()));
            }
        }

        return new DiscoveryResult(registered, failed, Collections.unmodifiableList(errors));
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

    private void registerSkill(Path skillMd) throws SQLException {
        ParsedSkill parsed = SkillMarkdownParser.parse(skillMd);
        String sql = SkillSqlConverter.convert(parsed.frontmatter(), skillMd.getParent());

        try (Connection conn = DriverManager.getConnection(jdbcUrl);
             Statement stmt = conn.createStatement()) {
            stmt.executeUpdate(sql);
        }
    }
}
