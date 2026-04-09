package org.specdriven.skill.sql;

import java.util.Objects;

/**
 * Parsed YAML frontmatter from a SKILL.md file.
 */
public record SkillFrontmatter(
        String skillId,
        String name,
        String description,
        String author,
        String type,
        String version) {

    public SkillFrontmatter {
        Objects.requireNonNull(skillId, "skillId must not be null");
        Objects.requireNonNull(name, "name must not be null");
    }
}
