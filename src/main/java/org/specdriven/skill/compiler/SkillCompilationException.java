package org.specdriven.skill.compiler;

public class SkillCompilationException extends RuntimeException {

    public SkillCompilationException(String message) {
        super(message);
    }

    public SkillCompilationException(String message, Throwable cause) {
        super(message, cause);
    }
}
