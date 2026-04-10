package org.specdriven.agent.question;

import java.util.Objects;

/**
 * Built-in {@link MaskingStrategy} covering common patterns:
 * email addresses, API keys/tokens, and generic short values.
 */
public final class DefaultMaskingStrategy implements MaskingStrategy {

    public static final DefaultMaskingStrategy INSTANCE = new DefaultMaskingStrategy();

    static final String PLACEHOLDER = "****";
    static final char MASK_CHAR = '*';

    private DefaultMaskingStrategy() {}

    @Override
    public String mask(String fieldName, String value) {
        Objects.requireNonNull(fieldName, "fieldName must not be null");
        if (value == null || value.isEmpty()) {
            return PLACEHOLDER;
        }
        if (isEmail(value)) {
            return maskEmail(value);
        }
        if (value.length() > 8) {
            return maskLongValue(value);
        }
        return PLACEHOLDER;
    }

    private static boolean isEmail(String value) {
        int at = value.indexOf('@');
        return at > 0 && at < value.length() - 1;
    }

    private static String maskEmail(String email) {
        int at = email.indexOf('@');
        String local = email.substring(0, at);
        String reveal = local.length() >= 2 ? local.substring(0, 2) : local;
        String domain = email.substring(at + 1);
        String maskedDomain = String.valueOf(MASK_CHAR).repeat(domain.length());
        return reveal + "***@" + maskedDomain;
    }

    private static String maskLongValue(String value) {
        String reveal = value.substring(0, Math.min(4, value.length()));
        int remaining = value.length() - reveal.length();
        return reveal + String.valueOf(MASK_CHAR).repeat(remaining);
    }
}
