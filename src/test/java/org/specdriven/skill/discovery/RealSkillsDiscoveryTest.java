package org.specdriven.skill.discovery;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration test: runs discoverAndRegister() against the real auto-spec-driven skills directory.
 * Skipped when the reference project is not available.
 */
@DisabledOnOs(OS.WINDOWS)
class RealSkillsDiscoveryTest {

    private static final Path SKILLS_DIR = Path.of("/home/wx766/Code/auto-spec-driven/skills");

    @Test
    void registersAllRealSkills() {
        if (!Files.isDirectory(SKILLS_DIR)) {
            return; // skip if reference project not available
        }

        String dbName = "test_real_discovery_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        String jdbcUrl = "jdbc:lealone:embed:" + dbName + "?PERSISTENT=false";

        DiscoveryResult result = new SkillAutoDiscovery(jdbcUrl, SKILLS_DIR).discoverAndRegister();

        // Log any failures for diagnosis
        if (!result.errors().isEmpty()) {
            result.errors().forEach(e ->
                System.err.println("Failed: " + e.path() + " — " + e.errorMessage()));
        }

        assertEquals(0, result.failedCount(),
                "Expected no failures but got: " + result.errors());
        assertTrue(result.registeredCount() >= 19,
                "Expected at least 19 skills registered, got " + result.registeredCount());
    }
}
