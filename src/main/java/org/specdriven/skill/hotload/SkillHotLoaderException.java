package org.specdriven.skill.hotload;

public class SkillHotLoaderException extends RuntimeException {

    public SkillHotLoaderException(String message) {
        super(message);
    }

    public SkillHotLoaderException(String message, Throwable cause) {
        super(message, cause);
    }
}
