package org.specdriven.skill.sql;

import java.nio.file.Path;

/**
 * Converts parsed SKILL.md frontmatter into a Lealone CREATE SERVICE SQL statement.
 */
public final class SkillSqlConverter {

    private static final String PACKAGE_NAME = "org.specdriven.skill";
    private static final String EXECUTOR_PACKAGE = PACKAGE_NAME + ".executor";

    private SkillSqlConverter() {}

    /**
     * Convert a parsed skill to a CREATE SERVICE SQL statement.
     *
     * @param frontmatter parsed YAML frontmatter
     * @param skillDir    directory containing the SKILL.md file
     * @return complete CREATE SERVICE SQL string
     */
    public static String convert(SkillFrontmatter frontmatter, Path skillDir) {
        String serviceName = escapeSql(backtickWrap(frontmatter.name()));
        String className = toPascalCase(frontmatter.name()) + "Executor";

        StringBuilder sb = new StringBuilder();
        sb.append("CREATE SERVICE IF NOT EXISTS ").append(serviceName).append(" (\n");
        sb.append("    execute(prompt varchar) varchar\n");
        sb.append(")");
        if (frontmatter.description() != null && !frontmatter.description().isEmpty()) {
            sb.append(" COMMENT '").append(escapeSql(frontmatter.description())).append("'");
        }
        sb.append("\n");
        sb.append("LANGUAGE 'skill'\n");
        sb.append("PACKAGE '").append(PACKAGE_NAME).append("'\n");
        sb.append("IMPLEMENT BY '").append(EXECUTOR_PACKAGE).append(".").append(className).append("'\n");
        sb.append("PARAMETERS ");
        sb.append("'skill_id' '").append(escapeSql(frontmatter.skillId())).append("'");
        sb.append(", 'type' '").append(escapeSql(nullToEmpty(frontmatter.type()))).append("'");
        sb.append(", 'version' '").append(escapeSql(nullToEmpty(frontmatter.version()))).append("'");
        sb.append(", 'author' '").append(escapeSql(nullToEmpty(frontmatter.author()))).append("'");
        sb.append(", 'skill_dir' '").append(escapeSql(skillDir.toAbsolutePath().toString())).append("'");

        return sb.toString();
    }

    static String toPascalCase(String kebab) {
        StringBuilder sb = new StringBuilder();
        for (String part : kebab.split("-")) {
            if (!part.isEmpty()) {
                sb.append(Character.toUpperCase(part.charAt(0)));
                if (part.length() > 1) {
                    sb.append(part.substring(1));
                }
            }
        }
        return sb.toString();
    }

    static String escapeSql(String value) {
        if (value == null) return "";
        return value.replace("'", "''");
    }

    private static String backtickWrap(String name) {
        return "`" + name + "`";
    }

    private static String nullToEmpty(String value) {
        return value != null ? value : "";
    }
}
