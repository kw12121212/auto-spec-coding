package org.specdriven.agent.agent;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

final class SetLlmStatementParser {

    private static final String PREFIX = "SET LLM";

    Map<String, String> parseAssignments(String sql) {
        if (sql == null || sql.isBlank()) {
            throw new SetLlmSqlException("SET LLM SQL must not be null or blank");
        }

        String trimmed = sql.trim();
        if (trimmed.endsWith(";")) {
            trimmed = trimmed.substring(0, trimmed.length() - 1).trim();
        }
        if (!trimmed.regionMatches(true, 0, PREFIX, 0, PREFIX.length())) {
            throw new SetLlmSqlException("SET LLM SQL must start with 'SET LLM'");
        }
        if (trimmed.length() > PREFIX.length() && !Character.isWhitespace(trimmed.charAt(PREFIX.length()))) {
            throw new SetLlmSqlException("SET LLM SQL must start with 'SET LLM'");
        }

        String body = trimmed.substring(PREFIX.length()).trim();
        if (body.isEmpty()) {
            throw new SetLlmSqlException("SET LLM SQL must include at least one assignment");
        }
        return parseKeyValuePairs(body);
    }

    private static Map<String, String> parseKeyValuePairs(String body) {
        Map<String, String> values = new LinkedHashMap<>();
        int index = 0;
        while (index < body.length()) {
            index = skipDelimiters(body, index);
            if (index >= body.length()) {
                break;
            }

            int keyStart = index;
            while (index < body.length() && isIdentifierChar(body.charAt(index))) {
                index++;
            }
            if (keyStart == index) {
                throw new SetLlmSqlException("Malformed SET LLM SQL: expected parameter name");
            }

            String key = body.substring(keyStart, index).toLowerCase(Locale.ROOT);
            index = skipWhitespace(body, index);
            if (index >= body.length() || body.charAt(index) != '=') {
                throw new SetLlmSqlException("Malformed SET LLM SQL: expected '=' after parameter '" + key + "'");
            }
            index = skipWhitespace(body, index + 1);
            if (index >= body.length()) {
                throw new SetLlmSqlException("Malformed SET LLM SQL: missing value for parameter '" + key + "'");
            }

            ParsedValue parsedValue = parseValue(body, index);
            values.put(key, parsedValue.value());
            index = parsedValue.nextIndex();
        }

        if (values.isEmpty()) {
            throw new SetLlmSqlException("SET LLM SQL must include at least one assignment");
        }
        return Map.copyOf(values);
    }

    private static ParsedValue parseValue(String body, int startIndex) {
        if (body.charAt(startIndex) == '\'') {
            StringBuilder value = new StringBuilder();
            int index = startIndex + 1;
            while (index < body.length()) {
                char current = body.charAt(index);
                if (current == '\'') {
                    if (index + 1 < body.length() && body.charAt(index + 1) == '\'') {
                        value.append('\'');
                        index += 2;
                        continue;
                    }
                    return new ParsedValue(value.toString(), index + 1);
                }
                value.append(current);
                index++;
            }
            throw new SetLlmSqlException("Malformed SET LLM SQL: unterminated quoted value");
        }

        int index = startIndex;
        while (index < body.length() && body.charAt(index) != ',') {
            index++;
        }
        String value = body.substring(startIndex, index).trim();
        if (value.isEmpty()) {
            throw new SetLlmSqlException("Malformed SET LLM SQL: missing value");
        }
        return new ParsedValue(value, index);
    }

    private static int skipDelimiters(String text, int startIndex) {
        int index = startIndex;
        while (index < text.length()) {
            char current = text.charAt(index);
            if (Character.isWhitespace(current) || current == ',') {
                index++;
                continue;
            }
            break;
        }
        return index;
    }

    private static int skipWhitespace(String text, int startIndex) {
        int index = startIndex;
        while (index < text.length() && Character.isWhitespace(text.charAt(index))) {
            index++;
        }
        return index;
    }

    private static boolean isIdentifierChar(char c) {
        return Character.isLetterOrDigit(c) || c == '_';
    }

    private record ParsedValue(String value, int nextIndex) {
    }
}
