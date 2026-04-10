package org.specdriven.agent.question;

import java.util.Map;

/**
 * {@link QuestionMessageTemplate} for Discord using markdown bold markers.
 */
public final class DiscordMessageTemplate extends QuestionMessageTemplate {

    public static final String CHANNEL_TYPE = "discord";

    public DiscordMessageTemplate() {
        this(null, null, null);
    }

    public DiscordMessageTemplate(Map<String, TemplateFieldPolicy> fieldPolicies,
                                  MaskingStrategy maskingStrategy,
                                  String defaultText) {
        super(CHANNEL_TYPE, fieldPolicies, maskingStrategy, defaultText);
    }

    @Override
    protected String renderFields(Map<String, String> renderedFields) {
        StringBuilder sb = new StringBuilder();
        appendBold(sb, "Question", renderedFields.get("question"));
        appendBold(sb, "Impact", renderedFields.get("impact"));
        appendBold(sb, "Recommendation", renderedFields.get("recommendation"));
        appendBold(sb, "Session", renderedFields.get("sessionId"));
        appendBold(sb, "Question ID", renderedFields.get("questionId"));
        return sb.toString();
    }

    private static void appendBold(StringBuilder sb, String label, String value) {
        if (sb.length() > 0) {
            sb.append('\n');
        }
        sb.append("**").append(label).append(":** ").append(value);
    }
}
