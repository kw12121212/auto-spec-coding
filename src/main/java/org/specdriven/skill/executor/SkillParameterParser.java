package org.specdriven.skill.executor;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

import org.specdriven.skill.sql.SkillSqlException;

final class SkillParameterParser {

    private static final String PARAMETERS = "PARAMETERS";

    SkillParameters parse(String createSql) {
        if (createSql == null || createSql.isBlank()) {
            throw new SkillSqlException("Service CREATE SQL must not be null or blank");
        }

        int parametersIndex = indexOfIgnoreCase(createSql, PARAMETERS);
        if (parametersIndex < 0) {
            throw new SkillSqlException("Missing PARAMETERS clause in service CREATE SQL");
        }

        String rawParameters = createSql.substring(parametersIndex + PARAMETERS.length()).trim();
        if (rawParameters.startsWith("(")) {
            rawParameters = unwrapParenthesized(rawParameters);
        }

        Map<String, String> parsed = parseQuotedPairs(rawParameters);
        String skillId = required(parsed, "skill_id");
        String skillDir = required(parsed, "skill_dir");
        return new SkillParameters(skillId, Path.of(skillDir));
    }

    private static Map<String, String> parseQuotedPairs(String rawParameters) {
        Map<String, String> parsed = new LinkedHashMap<>();
        int index = 0;
        while (index < rawParameters.length()) {
            index = skipDelimiters(rawParameters, index);
            if (index >= rawParameters.length()) {
                break;
            }
            if (rawParameters.charAt(index) != '\'') {
                break;
            }

            ParsedQuoted key = parseQuoted(rawParameters, index);
            index = skipWhitespace(rawParameters, key.nextIndex());
            if (index < rawParameters.length() && rawParameters.charAt(index) == '=') {
                index++;
                index = skipWhitespace(rawParameters, index);
            }
            if (index >= rawParameters.length() || rawParameters.charAt(index) != '\'') {
                throw new SkillSqlException("Malformed PARAMETERS clause: expected quoted value for key '" + key.value() + "'");
            }

            ParsedQuoted value = parseQuoted(rawParameters, index);
            parsed.put(key.value().toLowerCase(Locale.ROOT), value.value());
            index = value.nextIndex();
        }
        return parsed;
    }

    private static ParsedQuoted parseQuoted(String text, int startIndex) {
        StringBuilder value = new StringBuilder();
        int index = startIndex + 1;
        while (index < text.length()) {
            char current = text.charAt(index);
            if (current == '\'') {
                if (index + 1 < text.length() && text.charAt(index + 1) == '\'') {
                    value.append('\'');
                    index += 2;
                    continue;
                }
                return new ParsedQuoted(value.toString(), index + 1);
            }
            value.append(current);
            index++;
        }
        throw new SkillSqlException("Malformed PARAMETERS clause: unterminated quoted string");
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

    private static String required(Map<String, String> parsed, String key) {
        String value = parsed.get(key);
        if (value == null || value.isBlank()) {
            throw new SkillSqlException("Missing required parameter '" + key + "' in service CREATE SQL");
        }
        return value;
    }

    private static int indexOfIgnoreCase(String value, String needle) {
        return value.toLowerCase(Locale.ROOT).indexOf(needle.toLowerCase(Locale.ROOT));
    }

    private static String unwrapParenthesized(String rawParameters) {
        int depth = 0;
        for (int index = 0; index < rawParameters.length(); index++) {
            char current = rawParameters.charAt(index);
            if (current == '\'') {
                index = skipQuotedLiteral(rawParameters, index);
                continue;
            }
            if (current == '(') {
                depth++;
                continue;
            }
            if (current == ')') {
                depth--;
                if (depth == 0) {
                    return rawParameters.substring(1, index).trim();
                }
            }
        }
        throw new SkillSqlException("Malformed PARAMETERS clause: unbalanced parentheses");
    }

    private static int skipQuotedLiteral(String text, int quoteIndex) {
        int index = quoteIndex + 1;
        while (index < text.length()) {
            if (text.charAt(index) == '\'') {
                if (index + 1 < text.length() && text.charAt(index + 1) == '\'') {
                    index += 2;
                    continue;
                }
                return index;
            }
            index++;
        }
        throw new SkillSqlException("Malformed PARAMETERS clause: unterminated quoted string");
    }

    record SkillParameters(String skillId, Path skillDir) {}

    private record ParsedQuoted(String value, int nextIndex) {}
}
