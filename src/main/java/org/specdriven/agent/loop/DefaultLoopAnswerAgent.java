package org.specdriven.agent.loop;

import org.specdriven.agent.agent.LlmClient;
import org.specdriven.agent.agent.LlmResponse;
import org.specdriven.agent.agent.Message;
import org.specdriven.agent.agent.SystemMessage;
import org.specdriven.agent.agent.UserMessage;
import org.specdriven.agent.question.Answer;
import org.specdriven.agent.question.AnswerSource;
import org.specdriven.agent.question.DeliveryMode;
import org.specdriven.agent.question.Question;
import org.specdriven.agent.question.QuestionDecision;
import org.specdriven.agent.question.QuestionRuntime;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Default implementation of {@link LoopAnswerAgent} that performs a single-turn LLM call
 * to construct an answer, then submits it via {@link QuestionRuntime}.
 */
public class DefaultLoopAnswerAgent implements LoopAnswerAgent {

    private final LlmClient llmClient;
    private final QuestionRuntime questionRuntime;

    public DefaultLoopAnswerAgent(LlmClient llmClient, QuestionRuntime questionRuntime) {
        this.llmClient = Objects.requireNonNull(llmClient, "llmClient");
        this.questionRuntime = Objects.requireNonNull(questionRuntime, "questionRuntime");
    }

    @Override
    public AnswerResolution resolve(Question question, int timeoutSeconds) {
        ExecutorService executor = Executors.newSingleThreadExecutor(
                r -> Thread.ofVirtual().unstarted(r));
        try {
            Future<AnswerResolution> future = executor.submit(() -> runResolve(question));
            return future.get(timeoutSeconds, TimeUnit.SECONDS);
        } catch (TimeoutException e) {
            executor.shutdownNow();
            return new AnswerResolution.Escalated("timeout");
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            String msg = cause != null && cause.getMessage() != null
                    ? cause.getMessage() : "unknown error";
            return new AnswerResolution.Escalated(msg);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return new AnswerResolution.Escalated("interrupted");
        } finally {
            executor.shutdown();
        }
    }

    private AnswerResolution runResolve(Question question) {
        List<Message> messages = List.of(
                new SystemMessage(
                        "You are an AI assistant resolving a question in an autonomous development workflow. "
                                + "Provide a clear, concise answer that allows the workflow to continue.",
                        System.currentTimeMillis()),
                new UserMessage(buildPrompt(question), System.currentTimeMillis())
        );

        LlmResponse response = llmClient.chat(messages);
        String content = extractContent(response);

        Answer answer = new Answer(
                content,
                "AI agent single-turn analysis",
                "LoopAnswerAgent",
                AnswerSource.AI_AGENT,
                0.8,
                QuestionDecision.ANSWER_ACCEPTED,
                DeliveryMode.AUTO_AI_REPLY,
                null,
                System.currentTimeMillis()
        );

        questionRuntime.submitAnswer(question.sessionId(), question.questionId(), answer);
        return new AnswerResolution.Resolved(answer);
    }

    private static String buildPrompt(Question question) {
        return "Question: " + question.question() + "\n"
                + "Impact: " + question.impact() + "\n"
                + "Recommendation: " + question.recommendation() + "\n\n"
                + "Please provide a clear, concise answer to the question above.";
    }

    private static String extractContent(LlmResponse response) {
        if (response instanceof LlmResponse.TextResponse text) {
            return text.content();
        }
        return "Unable to generate a structured answer";
    }
}
