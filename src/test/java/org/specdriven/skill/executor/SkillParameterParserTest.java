package org.specdriven.skill.executor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.specdriven.skill.sql.SkillSqlException;

class SkillParameterParserTest {

    private final SkillParameterParser parser = new SkillParameterParser();

    @Test
    void parsesCurrentSqlConverterFormat() {
        SkillParameterParser.SkillParameters parameters = parser.parse("""
                CREATE SERVICE IF NOT EXISTS `demo` (
                    execute(prompt varchar) varchar
                )
                LANGUAGE 'skill'
                PACKAGE 'org.specdriven.skill'
                IMPLEMENT BY 'org.specdriven.skill.executor.DemoExecutor'
                PARAMETERS 'skill_id' 'demo_skill', 'type' 'agent_skill', 'skill_dir' '/tmp/demo'
                """);

        assertEquals("demo_skill", parameters.skillId());
        assertEquals(Path.of("/tmp/demo"), parameters.skillDir());
    }

    @Test
    void parsesParenthesizedParametersCaseInsensitively() {
        SkillParameterParser.SkillParameters parameters = parser.parse("""
                CREATE SERVICE demo LANGUAGE 'skill'
                PARAMETERS ('SKILL_ID'='demo_skill', 'SKILL_DIR'='/tmp/demo')
                """);

        assertEquals("demo_skill", parameters.skillId());
        assertEquals(Path.of("/tmp/demo"), parameters.skillDir());
    }

    @Test
    void unescapesQuotedValues() {
        SkillParameterParser.SkillParameters parameters = parser.parse("""
                CREATE SERVICE demo LANGUAGE 'skill'
                PARAMETERS ('skill_id'='demo''skill', 'skill_dir'='/tmp/it''s-demo')
                """);

        assertEquals("demo'skill", parameters.skillId());
        assertEquals(Path.of("/tmp/it's-demo"), parameters.skillDir());
    }

    @Test
    void missingRequiredParameterThrows() {
        assertThrows(SkillSqlException.class, () -> parser.parse("""
                CREATE SERVICE demo LANGUAGE 'skill'
                PARAMETERS ('skill_id'='demo_skill')
                """));
    }
}
