package org.specdriven.agent.question;

/**
 * Controls how individual question fields are rendered in a message template.
 */
public enum TemplateFieldPolicy {

    /** Render the field value as-is. */
    INCLUDE,

    /** Omit the field entirely from the output. */
    TRIM,

    /** Apply the masking strategy before rendering. */
    MASK
}
