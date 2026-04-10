package org.specdriven.agent.answer;

import org.specdriven.agent.agent.*;
import org.specdriven.agent.question.Answer;
import org.specdriven.agent.question.AnswerSource;
import org.specdriven.agent.question.Question;
import org.specdriven.agent.question.QuestionDecision;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.*;

/**
 * Service for generating structured answers via LLM.
 *
 * <p>This service coordinates LLM calls and builds structured {@link Answer} objects
 * from the LLM responses. It handles timeouts, error recovery, and ensures all
 * required answer fields are populated.
 */
public class AnswerGenerationService {

    private final LlmProviderRegistry providerRegistry;
    private final ExecutorService executor;

    public AnswerGenerationService(LlmProviderRegistry providerRegistry) {
        this.providerRegistry = Objects.requireNonNull(providerRegistry, "providerRegistry");
        this.executor = Executors.newVirtualThreadPerTaskExecutor();
    }

    /**
     * Generates an answer to the question using the provided context.
     *
     * @param config   the answer agent configuration
     * @param context  the cropped conversation context
     * @param question the question to answer
     * @return a structured answer
     * @throws AnswerAgentTimeoutException if the LLM call times out
     * @throws AnswerAgentException        if an error occurs during generation
     */
    public Answer generate(AnswerAgentConfig config, List<Message> context, Question question) {
        Objects.requireNonNull(config, "config");
        Objects.requireNonNull(context, "context");
        Objects.requireNonNull(question, "question");

        LlmProvider provider = providerRegistry.provider(config.providerName());
        LlmClient client = provider.createClient();

        String prompt = buildPrompt(question);
        List<Message> messages = buildMessages(context, prompt);

        LlmRequest request = new LlmRequest(
                messages,
                null,
                null,
                config.temperature(),
                config.maxTokens(),
                null
        );

        try {
            LlmResponse response = callWithTimeout(client, request, config.timeoutSeconds());
            return buildAnswer(response, question);
        } catch (TimeoutException e) {
            throw new AnswerAgentTimeoutException(config.timeoutSeconds(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AnswerAgentException("Answer generation was interrupted", e);
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof RuntimeException re) {
                throw new AnswerAgentException("LLM call failed: " + re.getMessage(), re);
            }
            throw new AnswerAgentException("LLM call failed: " + cause.getMessage(), cause);
        }
    }

    private String buildPrompt(Question question) {
        return String.format("""
                You are an AI assistant helping to answer a question based on conversation context.

                Question: %s
                Impact: %s
                Recommendation: %s

                Please provide a concise and helpful answer to the question.
                Your response should directly address the question while considering the impact and recommendation.
                """,
                question.question(),
                question.impact(),
                question.recommendation()
        );
    }

    private List<Message> buildMessages(List<Message> context, String prompt) {
        Message promptMessage = new UserMessage(prompt, System.currentTimeMillis());

        if (context.isEmpty()) {
            return List.of(promptMessage);
        }

        List<Message> messages = new java.util.ArrayList<>(context.size() + 1);
        messages.addAll(context);
        messages.add(promptMessage);
        return messages;
    }

    private LlmResponse callWithTimeout(LlmClient client, LlmRequest request, long timeoutSeconds)
            throws TimeoutException, InterruptedException, ExecutionException {
        Future<LlmResponse> future = executor.submit(() -> client.chat(request));
        return future.get(timeoutSeconds, TimeUnit.SECONDS);
    }

    private Answer buildAnswer(LlmResponse response, Question question) {
        String content;
        if (response instanceof LlmResponse.TextResponse textResponse) {
            content = textResponse.content();
        } else if (response instanceof LlmResponse.ToolCallResponse) {
            content = "The LLM requested tool calls instead of providing a direct answer.";
        } else {
            content = "Unable to generate answer from LLM response.";
        }

        if (content == null || content.isBlank()) {
            content = "No answer content provided by LLM.";
        }

        return new Answer(
                content,
                "Based on conversation context",
                "answer-agent-runtime",
                AnswerSource.AI_AGENT,
                0.9, // Default high confidence for AI agent answers
                QuestionDecision.ANSWER_ACCEPTED,
                question.deliveryMode(),
                null, // No escalation reason for auto AI reply
                System.currentTimeMillis()
        );
    }

    /**
     * Shuts down the executor service.
     */
    public void close() {
        executor.shutdown();
        try {
            if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}
