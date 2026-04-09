package org.specdriven.skill.sql;

/**
 * Exception thrown when SKILL.md parsing or SQL generation fails.
 */
public class SkillSqlException extends RuntimeException {

    public SkillSqlException(String message) {
        super(message);
    }

    public SkillSqlException(String message, Throwable cause) {
        super(message, cause);
    }
}
