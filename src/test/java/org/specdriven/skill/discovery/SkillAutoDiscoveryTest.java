package org.specdriven.skill.discovery;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.specdriven.skill.sql.SkillSqlException;

import java.nio.file.Files;
import java.nio.file.Path;
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
        assertTrue(result.errors().isEmpty());
    }

    @Test
    void subdirsWithoutSkillMd_areSkipped() throws Exception {
        Files.createDirectory(skillsDir.resolve("not-a-skill"));

        DiscoveryResult result = new SkillAutoDiscovery(jdbcUrl, skillsDir).discoverAndRegister();

        assertEquals(0, result.registeredCount());
        assertEquals(0, result.failedCount());
        assertTrue(result.errors().isEmpty());
    }

    @Test
    void malformedSkillMd_isCollectedAsError() throws Exception {
        Path skillDir = Files.createDirectory(skillsDir.resolve("bad-skill"));
        Files.writeString(skillDir.resolve("SKILL.md"), "no frontmatter here");

        DiscoveryResult result = new SkillAutoDiscovery(jdbcUrl, skillsDir).discoverAndRegister();

        assertEquals(0, result.registeredCount());
        assertEquals(1, result.failedCount());
        assertEquals(1, result.errors().size());
        assertNotNull(result.errors().get(0).errorMessage());
        assertEquals(skillDir.resolve("SKILL.md"), result.errors().get(0).path());
    }

    @Test
    void malformedSkillMd_doesNotStopOtherSkills() throws Exception {
        Path bad = Files.createDirectory(skillsDir.resolve("bad-skill"));
        Files.writeString(bad.resolve("SKILL.md"), "no frontmatter here");

        Path good = Files.createDirectory(skillsDir.resolve("good-skill"));
        Files.writeString(good.resolve("SKILL.md"), """
                ---
                skill_id: good_skill
                name: good-skill
                description: A valid skill
                author: test
                type: agent_skill
                version: 1.0.0
                ---
                Do something useful.
                """);

        DiscoveryResult result = new SkillAutoDiscovery(jdbcUrl, skillsDir).discoverAndRegister();

        // Both were processed — processing did not stop after the bad skill
        assertEquals(2, result.registeredCount() + result.failedCount());
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
}
