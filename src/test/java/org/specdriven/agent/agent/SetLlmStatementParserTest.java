package org.specdriven.agent.agent;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SetLlmStatementParserTest {

    private final SetLlmStatementParser parser = new SetLlmStatementParser();

    @Test
    void parsesAssignmentsCaseInsensitively() {
        Map<String, String> assignments = parser.parseAssignments(
                "SET LLM PROVIDER='openai', MODEL='gpt-4.1', TIMEOUT=30, MAX_RETRIES=2, BASE_URL='https://api.example.com/v1'");

        assertEquals("openai", assignments.get("provider"));
        assertEquals("gpt-4.1", assignments.get("model"));
        assertEquals("30", assignments.get("timeout"));
        assertEquals("2", assignments.get("max_retries"));
        assertEquals("https://api.example.com/v1", assignments.get("base_url"));
    }

    @Test
    void parsesQuotedEscapedValuesAndTrailingSemicolon() {
        Map<String, String> assignments = parser.parseAssignments(
                "SET LLM model='demo''model', base_url='https://api.example.com/it''s';");

        assertEquals("demo'model", assignments.get("model"));
        assertEquals("https://api.example.com/it's", assignments.get("base_url"));
    }

    @Test
    void rejectsMalformedStatement() {
        assertThrows(SetLlmSqlException.class, () -> parser.parseAssignments("SET LLM model"));
        assertThrows(SetLlmSqlException.class, () -> parser.parseAssignments("SET LLMX model='gpt-4.1'"));
        assertThrows(SetLlmSqlException.class, () -> parser.parseAssignments("SELECT 1"));
    }
}
