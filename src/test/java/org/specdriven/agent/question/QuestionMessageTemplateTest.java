package org.specdriven.agent.question;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class QuestionMessageTemplateTest {

    private static Question sampleQuestion() {
        return new Question(
                "q-1", "s-1",
                "Which approach?", "Wrong choice delays delivery",
                "Use option A",
                QuestionStatus.WAITING_FOR_ANSWER,
                QuestionCategory.PLAN_SELECTION,
                DeliveryMode.PUSH_MOBILE_WAIT_HUMAN
        );
    }

    private static Question questionWithDefaults() {
        return new Question(
                "q-2", "s-2",
                "What to do?", "Small delay",
                "Try again",
                QuestionStatus.WAITING_FOR_ANSWER,
                QuestionCategory.CLARIFICATION,
                DeliveryMode.AUTO_AI_REPLY
        );
    }

    @Test
    void allIncludePolicyContainsAllFields() {
        var template = new TestTemplate("test", Map.of(), null, null);
        String result = template.format(sampleQuestion());
        assertTrue(result.contains("Which approach?"));
        assertTrue(result.contains("Wrong choice delays delivery"));
        assertTrue(result.contains("Use option A"));
        assertTrue(result.contains("s-1"));
        assertTrue(result.contains("q-1"));
    }

    @Test
    void trimPolicyRemovesField() {
        var template = new TestTemplate("test",
                Map.of("sessionId", TemplateFieldPolicy.TRIM), null, null);
        String result = template.format(sampleQuestion());
        assertFalse(result.contains("s-1"));
        assertTrue(result.contains("Which approach?"));
    }

    @Test
    void maskPolicyAppliesMasking() {
        var template = new TestTemplate("test",
                Map.of("sessionId", TemplateFieldPolicy.MASK), null, null);
        String result = template.format(sampleQuestion());
        assertFalse(result.contains("s-1"));
        assertTrue(result.contains("****"));
        assertTrue(result.contains("Which approach?"));
    }

    @Test
    void channelTypeReturnsConfiguredType() {
        var template = new TestTemplate("my-channel", Map.of(), null, null);
        assertEquals("my-channel", template.channelType());
    }

    @Test
    void defaultTextNeverUsedForValidQuestion() {
        var template = new TestTemplate("test", Map.of(), null, "CUSTOM_DEFAULT");
        String result = template.format(questionWithDefaults());
        assertFalse(result.contains("CUSTOM_DEFAULT"));
        assertTrue(result.contains("Try again"));
    }

    @Test
    void nullMaskingStrategyUsesDefault() {
        var template = new TestTemplate("test", Map.of(), null, null);
        String result = template.format(sampleQuestion());
        assertNotNull(result);
        assertFalse(result.isEmpty());
    }

    private static class TestTemplate extends QuestionMessageTemplate {
        TestTemplate(String channelType,
                     Map<String, TemplateFieldPolicy> fieldPolicies,
                     MaskingStrategy maskingStrategy,
                     String defaultText) {
            super(channelType, fieldPolicies, maskingStrategy, defaultText);
        }

        @Override
        protected String renderFields(Map<String, String> renderedFields) {
            StringBuilder sb = new StringBuilder();
            for (Map.Entry<String, String> e : renderedFields.entrySet()) {
                if (sb.length() > 0) sb.append('\n');
                sb.append(e.getKey()).append(": ").append(e.getValue());
            }
            return sb.toString();
        }
    }
}
