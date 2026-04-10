package org.specdriven.agent.question;

/**
 * Transforms sensitive field values for safe display in channel messages.
 */
@FunctionalInterface
public interface MaskingStrategy {

    /**
     * Mask a field value.
     *
     * @param fieldName the field being masked
     * @param value     the value to mask (may be null)
     * @return a non-null masked representation
     */
    String mask(String fieldName, String value);
}
