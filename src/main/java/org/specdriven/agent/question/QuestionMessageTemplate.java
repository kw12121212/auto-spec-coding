package org.specdriven.agent.question;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Base class for channel-specific question message templates.
 * Applies field policy (include/trim/mask), default text substitution,
 * and delegates channel-specific formatting to subclasses.
 *
 * <p>Fields not present in the policy map default to {@link TemplateFieldPolicy#INCLUDE}.
 */
public abstract class QuestionMessageTemplate implements RichMessageFormatter {

    private final String channelType;
    private final Map<String, TemplateFieldPolicy> fieldPolicies;
    private final MaskingStrategy maskingStrategy;
    private final String defaultText;

    protected QuestionMessageTemplate(String channelType,
                                      Map<String, TemplateFieldPolicy> fieldPolicies,
                                      MaskingStrategy maskingStrategy,
                                      String defaultText) {
        this.channelType = Objects.requireNonNull(channelType, "channelType");
        this.fieldPolicies = fieldPolicies != null
                ? Collections.unmodifiableMap(new LinkedHashMap<>(fieldPolicies))
                : Map.of();
        this.maskingStrategy = maskingStrategy != null
                ? maskingStrategy
                : DefaultMaskingStrategy.INSTANCE;
        this.defaultText = defaultText != null ? defaultText : "N/A";
    }

    /**
     * Returns the channel type this template targets (e.g. "telegram", "discord").
     */
    public final String channelType() {
        return channelType;
    }

    @Override
    public final String format(Question question) {
        Map<String, String> fields = extractFields(question);
        Map<String, String> rendered = applyPolicies(fields);
        return renderFields(rendered);
    }

    /**
     * Render the filtered fields into a channel-specific message string.
     *
     * @param renderedFields field name to display-value map (TRIMmed fields omitted)
     * @return the formatted message for the target channel
     */
    protected abstract String renderFields(Map<String, String> renderedFields);

    private Map<String, String> extractFields(Question question) {
        Map<String, String> fields = new LinkedHashMap<>();
        fields.put("question", question.question());
        fields.put("impact", question.impact());
        fields.put("recommendation", question.recommendation());
        fields.put("sessionId", question.sessionId());
        fields.put("questionId", question.questionId());
        return fields;
    }

    private Map<String, String> applyPolicies(Map<String, String> fields) {
        Map<String, String> result = new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : fields.entrySet()) {
            String name = entry.getKey();
            String value = entry.getValue();
            TemplateFieldPolicy policy = fieldPolicies.getOrDefault(name, TemplateFieldPolicy.INCLUDE);

            switch (policy) {
                case TRIM:
                    break;
                case MASK:
                    result.put(name, maskingStrategy.mask(name, value));
                    break;
                case INCLUDE:
                default:
                    result.put(name, isBlank(value) ? defaultText : value);
                    break;
            }
        }
        return result;
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
