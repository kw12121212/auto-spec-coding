package org.specdriven.skill.discovery;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.specdriven.skill.compiler.SkillCompilationDiagnostic;
import org.specdriven.skill.hotload.SkillHotLoader;
import org.specdriven.skill.hotload.SkillLoadResult;
import org.specdriven.skill.sql.SkillMarkdownParser;
import org.specdriven.skill.sql.SkillSqlConverter;
import org.specdriven.skill.sql.SkillSqlException;

import java.nio.file.Path;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class SkillAutoDiscoveryTest {

    @TempDir
    Path skillsDir;

    private String jdbcUrl;

    @BeforeEach
    void setUp() {
        String dbName = "test_discovery_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        jdbcUrl = "jdbc:lealone:embed:" + dbName + "?PERSISTENT=false";
    }

    @Test
    void emptyDirectory_returnsZeroCounts() {
        DiscoveryResult result = new SkillAutoDiscovery(jdbcUrl, skillsDir).discoverAndRegister();

        assertEquals(0, result.registeredCount());
        assertEquals(0, result.failedCount());
        assertEquals(0, result.hotLoadedCount());
        assertEquals(0, result.hotLoadFailedCount());
        assertTrue(result.errors().isEmpty());
    }

    @Test
    void subdirsWithoutSkillMd_areSkipped() throws Exception {
        Files.createDirectory(skillsDir.resolve("not-a-skill"));

        DiscoveryResult result = new SkillAutoDiscovery(jdbcUrl, skillsDir).discoverAndRegister();

        assertEquals(0, result.registeredCount());
        assertEquals(0, result.failedCount());
        assertEquals(0, result.hotLoadedCount());
        assertEquals(0, result.hotLoadFailedCount());
        assertTrue(result.errors().isEmpty());
    }

    @Test
    void malformedSkillMd_isCollectedAsError() throws Exception {
        Path skillDir = Files.createDirectory(skillsDir.resolve("bad-skill"));
        Files.writeString(skillDir.resolve("SKILL.md"), "no frontmatter here");

        DiscoveryResult result = new SkillAutoDiscovery(jdbcUrl, skillsDir).discoverAndRegister();

        assertEquals(0, result.registeredCount());
        assertEquals(1, result.failedCount());
        assertEquals(0, result.hotLoadedCount());
        assertEquals(0, result.hotLoadFailedCount());
        assertEquals(1, result.errors().size());
        assertNotNull(result.errors().get(0).errorMessage());
        assertEquals(skillDir.resolve("SKILL.md"), result.errors().get(0).path());
    }

    @Test
    void malformedSkillMd_doesNotStopOtherSkills() throws Exception {
        Path bad = Files.createDirectory(skillsDir.resolve("bad-skill"));
        Files.writeString(bad.resolve("SKILL.md"), "no frontmatter here");

        Path good = createSkill("good-skill");

        DiscoveryResult result = new SkillAutoDiscovery(jdbcUrl, skillsDir).discoverAndRegister();

        // Both were processed — processing did not stop after the bad skill
        assertEquals(2, result.registeredCount() + result.failedCount());
        assertEquals(0, result.hotLoadedCount());
        assertEquals(0, result.hotLoadFailedCount());
        // Bad skill's parse error must be in errors
        assertTrue(result.errors().stream().anyMatch(e -> e.path().equals(bad.resolve("SKILL.md"))),
                "Expected bad skill parse error in errors, got: " + result.errors());
    }

    @Test
    void nonExistentSkillsDir_throwsSkillSqlException() {
        Path missing = skillsDir.resolve("does-not-exist");
        SkillAutoDiscovery discovery = new SkillAutoDiscovery(jdbcUrl, missing);

        assertThrows(SkillSqlException.class, discovery::discoverAndRegister);
    }

    @Test
    void errors_listIsUnmodifiable() throws Exception {
        DiscoveryResult result = new SkillAutoDiscovery(jdbcUrl, skillsDir).discoverAndRegister();

        assertThrows(UnsupportedOperationException.class,
                () -> result.errors().add(new SkillDiscoveryError(skillsDir, "test")));
    }

    @Test
    void matchingJavaSource_isHotLoadedBeforeRegistration() throws Exception {
        createSkill("good-skill");
        writeExecutorJavaSource("good-skill", "GoodSkillExecutor");
        RecordingHotLoader hotLoader = new RecordingHotLoader(new SkillLoadResult(true,
                "org.specdriven.skill.executor.GoodSkillExecutor", List.of()));

        DiscoveryResult result = new SkillAutoDiscovery(jdbcUrl, skillsDir, hotLoader).discoverAndRegister();

        assertEquals(1, result.registeredCount());
        assertEquals(0, result.failedCount());
        assertEquals(1, result.hotLoadedCount());
        assertEquals(0, result.hotLoadFailedCount());
        assertTrue(result.errors().isEmpty());
        assertEquals(1, hotLoader.calls.size());
        LoadCall call = hotLoader.calls.getFirst();
        assertEquals("good-skill", call.skillName());
        assertEquals("org.specdriven.skill.executor.GoodSkillExecutor", call.entryClassName());
        assertTrue(call.javaSource().contains("class GoodSkillExecutor"));
        assertFalse(call.sourceHash().isBlank());
    }

    @Test
    void missingJavaSource_skipsHotLoadAndStillRegistersSkill() throws Exception {
        createSkill("good-skill");
        RecordingHotLoader hotLoader = new RecordingHotLoader(new SkillLoadResult(true,
                "org.specdriven.skill.executor.GoodSkillExecutor", List.of()));

        DiscoveryResult result = new SkillAutoDiscovery(jdbcUrl, skillsDir, hotLoader).discoverAndRegister();

        assertEquals(1, result.registeredCount());
        assertEquals(0, result.failedCount());
        assertEquals(0, result.hotLoadedCount());
        assertEquals(0, result.hotLoadFailedCount());
        assertTrue(result.errors().isEmpty());
        assertTrue(hotLoader.calls.isEmpty());
    }

    @Test
    void hotLoadFailure_isReportedWithoutChangingSqlFailureCount() throws Exception {
        createSkill("good-skill");
        Path javaSource = writeExecutorJavaSource("good-skill", "GoodSkillExecutor");
        RecordingHotLoader hotLoader = new RecordingHotLoader(new SkillLoadResult(false,
                "org.specdriven.skill.executor.GoodSkillExecutor",
                List.of(new SkillCompilationDiagnostic("compile failed", -1, -1))));

        DiscoveryResult result = new SkillAutoDiscovery(jdbcUrl, skillsDir, hotLoader).discoverAndRegister();

        assertEquals(1, result.registeredCount());
        assertEquals(0, result.failedCount());
        assertEquals(0, result.hotLoadedCount());
        assertEquals(1, result.hotLoadFailedCount());
        assertEquals(1, result.errors().size());
        assertEquals(javaSource, result.errors().getFirst().path());
        assertTrue(result.errors().getFirst().errorMessage().contains("compile failed"));
    }

    @Test
    void generatedSqlContainsSkillDir() throws Exception {
        Path skillDir = createSkill("my-skill");

        SkillMarkdownParser.ParsedSkill parsed = SkillMarkdownParser.parse(skillDir.resolve("SKILL.md"));
        String sql = SkillSqlConverter.convert(parsed.frontmatter(), skillDir);

        assertTrue(sql.contains("skill_dir = '" + skillDir.toAbsolutePath() + "'"),
                "Expected skill_dir in SQL but got: " + sql);
        assertFalse(sql.contains("'instructions'"),
                "Expected no inline instructions in SQL but got: " + sql);
    }

    private Path createSkill(String skillName) throws Exception {
        Path skillDir = Files.createDirectory(skillsDir.resolve(skillName));
        Files.writeString(skillDir.resolve("SKILL.md"), """
                ---
                skill_id: %s
                name: %s
                description: A valid skill
                author: test
                type: agent_skill
                version: 1.0.0
                ---
                Do something useful.
                """.formatted(skillName.replace('-', '_'), skillName));
        return skillDir;
    }

    private Path writeExecutorJavaSource(String skillName, String className) throws Exception {
        Path skillDir = skillsDir.resolve(skillName);
        Path javaSource = skillDir.resolve(className + ".java");
        Files.writeString(javaSource, """
                package org.specdriven.skill.executor;
                public class %s {
                    public String run() {
                        return \"ok\";
                    }
                }
                """.formatted(className));
        return javaSource;
    }

    private record LoadCall(String skillName, String entryClassName, String javaSource, String sourceHash) {
    }

    private static final class RecordingHotLoader implements SkillHotLoader {
        private final boolean activationEnabled;
        private final SkillLoadResult nextResult;
        private final List<LoadCall> calls = new ArrayList<>();

        private RecordingHotLoader(SkillLoadResult nextResult) {
            this(true, nextResult);
        }

        private RecordingHotLoader(boolean activationEnabled, SkillLoadResult nextResult) {
            this.activationEnabled = activationEnabled;
            this.nextResult = nextResult;
        }

        @Override
        public boolean isActivationEnabled() {
            return activationEnabled;
        }

        @Override
        public SkillLoadResult load(String skillName, String entryClassName, String javaSource, String sourceHash) {
            calls.add(new LoadCall(skillName, entryClassName, javaSource, sourceHash));
            return nextResult;
        }

        @Override
        public SkillLoadResult replace(String skillName, String entryClassName, String javaSource, String sourceHash) {
            throw new UnsupportedOperationException("replace not used in this test");
        }

        @Override
        public void unload(String skillName) {
        }

        @Override
        public Optional<ClassLoader> activeLoader(String skillName) {
            return Optional.empty();
        }

        @Override
        public Set<String> loadedSkillNames() {
            return Set.of();
        }

        @Override
        public Set<String> failedSkillNames() {
            return Set.of();
        }
    }

    @Test
    void disabledHotLoader_doesNotBlockSqlRegistration() throws Exception {
        createSkill("good-skill");
        Path javaSource = writeExecutorJavaSource("good-skill", "GoodSkillExecutor");
        RecordingHotLoader hotLoader = new RecordingHotLoader(false, new SkillLoadResult(false,
                "org.specdriven.skill.executor.GoodSkillExecutor",
                List.of(new SkillCompilationDiagnostic("Hot-loading is disabled; explicit programmatic enablement is required", -1, -1))));

        DiscoveryResult result = new SkillAutoDiscovery(jdbcUrl, skillsDir, hotLoader).discoverAndRegister();

        assertEquals(1, result.registeredCount());
        assertEquals(0, result.failedCount());
        assertEquals(0, result.hotLoadedCount());
        assertEquals(1, result.hotLoadFailedCount());
        assertEquals(1, result.errors().size());
        assertEquals(javaSource, result.errors().getFirst().path());
        assertTrue(result.errors().getFirst().errorMessage().contains("disabled"));
    }
}
