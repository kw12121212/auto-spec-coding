package org.specdriven.agent.tool;

/**
 * Describes a single parameter that a tool accepts.
 *
 * @param name        the parameter name
 * @param type        the parameter type (e.g. "string", "integer", "boolean")
 * @param description human-readable description of what this parameter controls
 * @param required    whether this parameter must be provided
 */
public record ToolParameter(
        String name,
        String type,
        String description,
        boolean required
) {}
