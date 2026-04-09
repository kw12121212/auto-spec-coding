package org.specdriven.skill.sql;

import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

/**
 * Parses a SKILL.md file, extracting YAML frontmatter and instruction body.
 */
public final class SkillMarkdownParser {

    private static final Yaml YAML = new Yaml();

    private SkillMarkdownParser() {}

    /**
     * Parse a SKILL.md file into frontmatter and instruction body.
     *
     * @param path path to the SKILL.md file
     * @return parsed result containing frontmatter and instruction body
     * @throws SkillSqlException if the file cannot be read or has invalid frontmatter
     */
    public static ParsedSkill parse(Path path) {
        String content;
        try {
            content = Files.readString(path);
        } catch (IOException e) {
            throw new SkillSqlException("Failed to read SKILL.md: " + path.toAbsolutePath(), e);
        }

        String yamlBlock = extractYamlBlock(content, path);
        String instructionBody = extractInstructionBody(content, path);

        Map<String, Object> data = parseYaml(yamlBlock, path);

        String skillId = requireString(data, "skill_id", path);
        String name = requireString(data, "name", path);

        return new ParsedSkill(
                new SkillFrontmatter(
                        skillId,
                        name,
                        asString(data.get("description")),
                        asString(data.get("author")),
                        asString(data.get("type")),
                        asString(data.get("version"))),
                instructionBody);
    }

    private static String extractYamlBlock(String content, Path path) {
        int first = content.indexOf("---");
        if (first < 0) {
            throw new SkillSqlException("Missing opening --- in SKILL.md: " + path.toAbsolutePath());
        }
        int second = content.indexOf("---", first + 3);
        if (second < 0) {
            throw new SkillSqlException("Missing closing --- in SKILL.md: " + path.toAbsolutePath());
        }
        return content.substring(first + 3, second).trim();
    }

    private static String extractInstructionBody(String content, Path path) {
        int first = content.indexOf("---");
        int second = content.indexOf("---", first + 3);
        int bodyStart = second + 3;
        if (bodyStart >= content.length()) {
            return "";
        }
        return content.substring(bodyStart).stripLeading();
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> parseYaml(String yamlBlock, Path path) {
        try {
            return YAML.loadAs(yamlBlock, Map.class);
        } catch (Exception e) {
            throw new SkillSqlException("Invalid YAML in SKILL.md: " + path.toAbsolutePath(), e);
        }
    }

    private static String requireString(Map<String, Object> data, String key, Path path) {
        Object value = data.get(key);
        if (value == null) {
            throw new SkillSqlException(
                    "Missing required field '" + key + "' in SKILL.md: " + path.toAbsolutePath());
        }
        return value.toString();
    }

    private static String asString(Object value) {
        return value != null ? value.toString() : null;
    }

    /**
     * Result of parsing a SKILL.md file.
     */
    public record ParsedSkill(SkillFrontmatter frontmatter, String instructionBody) {}
}
