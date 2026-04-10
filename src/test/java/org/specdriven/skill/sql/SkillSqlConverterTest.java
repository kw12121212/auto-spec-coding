package org.specdriven.skill.sql;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class SkillSqlConverterTest {

    @TempDir
    Path skillDir;

    private final SkillFrontmatter sampleFrontmatter = new SkillFrontmatter(
            "spec_driven_propose", "spec-driven-propose",
            "Propose a new change.", "auto-spec-driven", "agent_skill", "1.0.0");

    @Test
    void generatesCompleteSql() {
        String sql = SkillSqlConverter.convert(sampleFrontmatter, skillDir);

        assertTrue(sql.startsWith("CREATE SERVICE IF NOT EXISTS `spec-driven-propose`"));
        assertTrue(sql.contains("execute(prompt varchar) varchar"));
        assertTrue(sql.contains("LANGUAGE 'skill'"));
        assertTrue(sql.contains("PACKAGE 'org.specdriven.skill'"));
        assertTrue(sql.contains("IMPLEMENT BY 'org.specdriven.skill.executor.SpecDrivenProposeExecutor'"));
    }

    @Test
    void includesParameters() {
        String sql = SkillSqlConverter.convert(sampleFrontmatter, skillDir);

        assertTrue(sql.contains("skill_id = 'spec_driven_propose'"));
        assertTrue(sql.contains("type = 'agent_skill'"));
        assertTrue(sql.contains("version = '1.0.0'"));
        assertTrue(sql.contains("author = 'auto-spec-driven'"));
        assertTrue(sql.contains("skill_dir = '" + skillDir.toAbsolutePath() + "'"));
    }

    @Test
    void doesNotIncludeInlineInstructions() {
        String sql = SkillSqlConverter.convert(sampleFrontmatter, skillDir);

        assertFalse(sql.contains("'instructions'"));
    }

    @Test
    void escapesSingleQuotesInSkillDir() {
        Path pathWithQuote = Path.of("/some/it's/path");
        SkillFrontmatter fm = new SkillFrontmatter(
                "test_id", "test-name", null, "author", "type", "1.0.0");
        String sql = SkillSqlConverter.convert(fm, pathWithQuote);

        assertTrue(sql.contains("skill_dir = '/some/it''s/path'"));
    }

    @Test
    void toPascalCase() {
        assertEquals("SpecDrivenPropose", SkillSqlConverter.toPascalCase("spec-driven-propose"));
        assertEquals("Init", SkillSqlConverter.toPascalCase("init"));
        assertEquals("AB", SkillSqlConverter.toPascalCase("a-b"));
    }

    @Test
    void escapeSql() {
        assertEquals("it''s", SkillSqlConverter.escapeSql("it's"));
        assertEquals("", SkillSqlConverter.escapeSql(null));
        assertEquals("plain", SkillSqlConverter.escapeSql("plain"));
    }
}
