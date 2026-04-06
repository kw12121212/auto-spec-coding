package org.specdriven.agent.config;

/**
 * Runtime exception for configuration errors (missing files, malformed YAML, missing keys).
 */
public class ConfigException extends RuntimeException {

    public ConfigException(String message) {
        super(message);
    }

    public ConfigException(String message, Throwable cause) {
        super(message, cause);
    }
}
