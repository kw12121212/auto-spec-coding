package org.specdriven.skill.compiler;

public class ClassCacheException extends RuntimeException {

    public ClassCacheException(String message) {
        super(message);
    }

    public ClassCacheException(String message, Throwable cause) {
        super(message, cause);
    }
}
