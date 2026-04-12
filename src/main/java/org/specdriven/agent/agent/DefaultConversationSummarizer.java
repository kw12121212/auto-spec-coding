package org.specdriven.agent.agent;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.StringJoiner;

/**
 * Default deterministic conversation summarizer backed by retention policy decisions.
 */
public final class DefaultConversationSummarizer implements ConversationSummarizer {

    private static final String SUMMARY_PREFIX = "Conversation summary:";

    private final ContextRetentionPolicy retentionPolicy;

    public DefaultConversationSummarizer() {
        this(new DefaultContextRetentionPolicy());
    }

    public DefaultConversationSummarizer(ContextRetentionPolicy retentionPolicy) {
        this.retentionPolicy = Objects.requireNonNull(retentionPolicy, "retentionPolicy");
    }

    @Override
    public List<Message> summarize(ConversationSummarizerInput input) {
        Objects.requireNonNull(input, "input");
        List<Message> messages = input.messages();
        if (messages.isEmpty() || TokenCounter.estimate(messages) <= input.tokenBudget()) {
            return List.copyOf(messages);
        }

        boolean[] retained = retainedMessages(input);
        List<Message> compressed = new ArrayList<>();
        for (int i = 0; i < messages.size(); i++) {
            if (!retained[i]) {
                compressed.add(messages.get(i));
            }
        }
        if (compressed.isEmpty()) {
            return retainedOnly(messages, retained);
        }

        ConversationSummary summary = createSummary(compressed, input.summaryTokenBudget());
        List<Message> summarized = new ArrayList<>();
        for (int i = 0; i < messages.size(); i++) {
            if (retained[i] && messages.get(i) instanceof SystemMessage) {
                summarized.add(messages.get(i));
            }
        }
        summarized.add(summary.toMessage(summaryTimestamp(compressed)));
        for (int i = 0; i < messages.size(); i++) {
            if (retained[i] && !(messages.get(i) instanceof SystemMessage)) {
                summarized.add(messages.get(i));
            }
        }
        return List.copyOf(summarized);
    }

    private boolean[] retainedMessages(ConversationSummarizerInput input) {
        List<Message> messages = input.messages();
        boolean[] retained = new boolean[messages.size()];
        int recentStart = Math.max(0, messages.size() - input.recentMessageLimit());
        for (int i = 0; i < messages.size(); i++) {
            Message message = messages.get(i);
            if (message instanceof SystemMessage || i >= recentStart || mandatory(input, message)) {
                retained[i] = true;
            }
        }
        return retained;
    }

    private boolean mandatory(ConversationSummarizerInput input, Message message) {
        ContextRetentionDecision decision = retentionPolicy.evaluate(input.retentionCandidateFor(message));
        return decision.mandatory();
    }

    private static List<Message> retainedOnly(List<Message> messages, boolean[] retained) {
        List<Message> result = new ArrayList<>();
        for (int i = 0; i < messages.size(); i++) {
            if (retained[i]) {
                result.add(messages.get(i));
            }
        }
        return List.copyOf(result);
    }

    private ConversationSummary createSummary(List<Message> compressed, int summaryTokenBudget) {
        LinkedHashMap<String, Integer> roleCounts = new LinkedHashMap<>();
        LinkedHashMap<String, Integer> toolCounts = new LinkedHashMap<>();
        List<String> excerpts = new ArrayList<>();
        for (Message message : compressed) {
            roleCounts.merge(message.role(), 1, Integer::sum);
            if (message instanceof ToolMessage toolMessage && toolMessage.toolName() != null
                    && !toolMessage.toolName().isBlank()) {
                toolCounts.merge(toolMessage.toolName(), 1, Integer::sum);
            }
            if (excerpts.size() < 3) {
                excerpts.add(excerpt(message));
            }
        }

        String content = summaryContent(compressed.size(), roleCounts, toolCounts, excerpts);
        while (TokenCounter.estimate(content) > summaryTokenBudget && !excerpts.isEmpty()) {
            excerpts.remove(excerpts.size() - 1);
            content = summaryContent(compressed.size(), roleCounts, toolCounts, excerpts);
        }
        return new ConversationSummary(compressed.size(), roleCounts, toolCounts, content);
    }

    private static String summaryContent(int messageCount, Map<String, Integer> roleCounts,
            Map<String, Integer> toolCounts, List<String> excerpts) {
        StringBuilder content = new StringBuilder(SUMMARY_PREFIX)
                .append(" compressed ")
                .append(messageCount)
                .append(messageCount == 1 ? " earlier message." : " earlier messages.")
                .append(" Roles: ")
                .append(joinCounts(roleCounts))
                .append(".");
        if (!toolCounts.isEmpty()) {
            content.append(" Tools: ").append(joinCounts(toolCounts)).append(".");
        }
        if (!excerpts.isEmpty()) {
            content.append(" Excerpts: ").append(String.join(" | ", excerpts)).append(".");
        }
        return content.toString();
    }

    private static String joinCounts(Map<String, Integer> counts) {
        StringJoiner joiner = new StringJoiner(", ");
        for (Map.Entry<String, Integer> entry : counts.entrySet()) {
            joiner.add(entry.getKey() + "=" + entry.getValue());
        }
        return joiner.toString();
    }

    private static String excerpt(Message message) {
        String content = message.content() == null ? "" : message.content().strip().replaceAll("\\s+", " ");
        if (content.length() > 48) {
            content = content.substring(0, 48).stripTrailing();
        }
        String label = message.role();
        if (message instanceof ToolMessage toolMessage && toolMessage.toolName() != null
                && !toolMessage.toolName().isBlank()) {
            label = "tool:" + toolMessage.toolName();
        }
        return label + " \"" + content + "\"";
    }

    private static long summaryTimestamp(List<Message> compressed) {
        long earliest = Long.MAX_VALUE;
        for (Message message : compressed) {
            earliest = Math.min(earliest, message.timestamp());
        }
        return earliest == Long.MAX_VALUE ? 0L : earliest;
    }
}
