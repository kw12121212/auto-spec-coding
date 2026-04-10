package org.specdriven.agent.question;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TemplateFieldPolicyTest {

    @Test
    void hasRequiredValues() {
        assertNotNull(TemplateFieldPolicy.INCLUDE);
        assertNotNull(TemplateFieldPolicy.TRIM);
        assertNotNull(TemplateFieldPolicy.MASK);
    }

    @Test
    void enumValuesCount() {
        assertEquals(3, TemplateFieldPolicy.values().length);
    }
}
