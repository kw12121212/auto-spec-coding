package org.specdriven.agent.question;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class DefaultMaskingStrategyTest {

    private final DefaultMaskingStrategy strategy = DefaultMaskingStrategy.INSTANCE;

    @Test
    void maskEmailRevealsPrefixAndMasksDomain() {
        String result = strategy.mask("email", "user@example.com");
        assertTrue(result.startsWith("us"));
        assertTrue(result.contains("@"));
        assertFalse(result.contains("example.com"));
    }

    @Test
    void maskShortEmailRevealsFullLocalPart() {
        String result = strategy.mask("email", "ab@x.io");
        assertTrue(result.startsWith("ab"));
        assertTrue(result.contains("@"));
    }

    @Test
    void maskApiKeyRevealsFirstFourChars() {
        String result = strategy.mask("apiKey", "sk-1234567890abcdef");
        assertTrue(result.startsWith("sk-1"));
        assertFalse(result.contains("abcdef"));
    }

    @Test
    void maskGenericShortValueReturnsPlaceholder() {
        String result = strategy.mask("code", "abc");
        assertEquals("****", result);
    }

    @Test
    void maskNullReturnsPlaceholder() {
        String result = strategy.mask("field", null);
        assertEquals("****", result);
    }

    @Test
    void maskEmptyReturnsPlaceholder() {
        String result = strategy.mask("field", "");
        assertEquals("****", result);
    }

    @Test
    void singletonInstance() {
        assertSame(DefaultMaskingStrategy.INSTANCE, DefaultMaskingStrategy.INSTANCE);
    }
}
