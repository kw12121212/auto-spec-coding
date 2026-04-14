package org.specdriven.skill.sql;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class SkillMarkdownParserTest {

    @TempDir
    Path tempDir;

    @Test
    void parsesValidFrontmatter() throws Exception {
        Path file = writeSkillMd("""
                ---
                skill_id: spec_driven_propose
                name: spec-driven-propose
                description: Propose a new change.
                author: auto-spec-driven
                type: agent_skill
                version: 1.0.0
                ---
                You are helping the user create a proposal.
                """);

        SkillMarkdownParser.ParsedSkill result = SkillMarkdownParser.parse(file);

        SkillFrontmatter fm = result.frontmatter();
        assertEquals("spec_driven_propose", fm.skillId());
        assertEquals("spec-driven-propose", fm.name());
        assertEquals("Propose a new change.", fm.description());
        assertEquals("auto-spec-driven", fm.author());
        assertEquals("agent_skill", fm.type());
        assertEquals("1.0.0", fm.version());
        assertEquals("You are helping the user create a proposal.\n", result.instructionBody());
    }

    @Test
    void parsesMetadataFrontmatter() throws Exception {
        Path file = writeSkillMd("""
                ---
                name: spec-driven-propose
                description: Propose a new change.
                metadata:
                  skill_id: spec_driven_propose
                  author: auto-spec-driven
                  type: agent_skill
                  version: 1.0.0
                ---
                You are helping the user create a proposal.
                """);

        SkillMarkdownParser.ParsedSkill result = SkillMarkdownParser.parse(file);

        SkillFrontmatter fm = result.frontmatter();
        assertEquals("spec_driven_propose", fm.skillId());
        assertEquals("spec-driven-propose", fm.name());
        assertEquals("Propose a new change.", fm.description());
        assertEquals("auto-spec-driven", fm.author());
        assertEquals("agent_skill", fm.type());
        assertEquals("1.0.0", fm.version());
    }

    @Test
    void topLevelMetadataFieldsTakePrecedence() throws Exception {
        Path file = writeSkillMd("""
                ---
                skill_id: top_level_id
                name: spec-driven-propose
                author: top-level-author
                metadata:
                  skill_id: nested_id
                  author: nested-author
                ---
                body
                """);

        SkillMarkdownParser.ParsedSkill result = SkillMarkdownParser.parse(file);

        assertEquals("top_level_id", result.frontmatter().skillId());
        assertEquals("top-level-author", result.frontmatter().author());
    }

    @Test
    void throwsWhenMissingFrontmatter() throws Exception {
        Path file = writeSkillMd("No frontmatter here.");

        SkillSqlException ex = assertThrows(SkillSqlException.class,
                () -> SkillMarkdownParser.parse(file));
        assertTrue(ex.getMessage().contains("Missing opening ---"));
    }

    @Test
    void throwsWhenMissingClosingMarker() throws Exception {
        Path file = writeSkillMd("""
                ---
                skill_id: test
                name: test
                """);

        SkillSqlException ex = assertThrows(SkillSqlException.class,
                () -> SkillMarkdownParser.parse(file));
        assertTrue(ex.getMessage().contains("Missing closing ---"));
    }

    @Test
    void throwsWhenRequiredFieldMissing() throws Exception {
        Path file = writeSkillMd("""
                ---
                metadata:
                  skill_id: test
                ---
                body
                """);

        SkillSqlException ex = assertThrows(SkillSqlException.class,
                () -> SkillMarkdownParser.parse(file));
        assertTrue(ex.getMessage().contains("Missing required field 'name'"));
    }

    @Test
    void emptyInstructionBody() throws Exception {
        Path file = writeSkillMd("""
                ---
                skill_id: test_id
                name: test-name
                ---
                """);

        SkillMarkdownParser.ParsedSkill result = SkillMarkdownParser.parse(file);

        assertEquals("", result.instructionBody());
        assertEquals("test-name", result.frontmatter().name());
    }

    @Test
    void handlesSingleQuotesInDescription() throws Exception {
        Path file = writeSkillMd("""
                ---
                skill_id: test_id
                name: test-name
                description: It's a "test" skill
                ---
                body
                """);

        SkillMarkdownParser.ParsedSkill result = SkillMarkdownParser.parse(file);
        assertEquals("It's a \"test\" skill", result.frontmatter().description());
    }

    private Path writeSkillMd(String content) throws Exception {
        Path file = tempDir.resolve("SKILL.md");
        Files.writeString(file, content);
        return file;
    }
}
