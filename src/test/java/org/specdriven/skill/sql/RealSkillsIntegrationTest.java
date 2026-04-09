package org.specdriven.skill.sql;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Verifies that all SKILL.md files from the auto-spec-driven reference project
 * parse successfully and generate valid SQL.
 */
@DisabledOnOs(OS.WINDOWS) // reference project path is Linux-specific
class RealSkillsIntegrationTest {

    private static final Path SKILLS_DIR = Path.of("/home/wx766/Code/auto-spec-driven/skills");

    @Test
    void parsesAllRealSkillFiles() throws Exception {
        if (!Files.isDirectory(SKILLS_DIR)) {
            return; // skip if reference project not available
        }

        try (Stream<Path> dirs = Files.list(SKILLS_DIR)) {
            long count = dirs.filter(Files::isDirectory)
                    .map(d -> d.resolve("SKILL.md"))
                    .filter(Files::exists)
                    .peek(skillMd -> {
                        SkillMarkdownParser.ParsedSkill result = SkillMarkdownParser.parse(skillMd);
                        assertNotNull(result.frontmatter().skillId());
                        assertNotNull(result.frontmatter().name());
                        assertFalse(result.frontmatter().name().isEmpty());

                        String sql = SkillSqlConverter.convert(
                                result.frontmatter(), result.instructionBody());
                        assertTrue(sql.startsWith("CREATE SERVICE IF NOT EXISTS"));
                        assertTrue(sql.contains("execute(prompt varchar) varchar"));
                        assertTrue(sql.contains("PARAMETERS"));
                    })
                    .count();

            assertTrue(count >= 19, "Expected at least 19 skills, found " + count);
        }
    }
}
