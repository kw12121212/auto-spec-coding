package org.specdriven.agent.answer;

import org.specdriven.agent.agent.LlmProviderRegistry;
import org.specdriven.agent.agent.Message;
import org.specdriven.agent.event.EventBus;
import org.specdriven.agent.question.Answer;
import org.specdriven.agent.question.Question;
import org.specdriven.agent.question.QuestionEvents;
import org.specdriven.agent.question.QuestionStatus;

import java.util.List;
import java.util.Objects;

/**
 * Runtime for Answer Agent that coordinates answer generation.
 *
 * <p>This is the main entry point for Answer Agent functionality. It manages
 * the context cropping, answer generation, and lifecycle events for automatic
 * question resolution.
 */
public class AnswerAgentRuntime implements AnswerAgent, AutoCloseable {

    private final AnswerAgentConfig config;
    private final AnswerContextManager contextManager;
    private final AnswerGenerationService generationService;
    private final EventBus eventBus;

    /**
     * Creates a new Answer Agent runtime with the given configuration.
     *
     * @param config           the answer agent configuration
     * @param providerRegistry the LLM provider registry
     * @param eventBus         the event bus for publishing lifecycle events
     */
    public AnswerAgentRuntime(AnswerAgentConfig config,
                              LlmProviderRegistry providerRegistry,
                              EventBus eventBus) {
        this.config = Objects.requireNonNull(config, "config");
        this.contextManager = new AnswerContextManager(config.maxContextMessages());
        this.generationService = new AnswerGenerationService(providerRegistry);
        this.eventBus = Objects.requireNonNull(eventBus, "eventBus");
    }

    /**
     * Creates a new Answer Agent runtime with default configuration.
     *
     * @param providerRegistry the LLM provider registry
     * @param eventBus         the event bus for publishing lifecycle events
     */
    public AnswerAgentRuntime(LlmProviderRegistry providerRegistry, EventBus eventBus) {
        this(AnswerAgentConfig.openAiMiniDefaults(), providerRegistry, eventBus);
    }

    @Override
    public Answer resolve(Question question, List<Message> messages) {
        Objects.requireNonNull(question, "question");
        Objects.requireNonNull(messages, "messages");

        if (question.status() != QuestionStatus.OPEN && question.status() != QuestionStatus.WAITING_FOR_ANSWER) {
            throw new AnswerAgentException("Question must be in OPEN or WAITING_FOR_ANSWER state, but was: " + question.status());
        }

        // Emit question created event
        eventBus.publish(QuestionEvents.questionCreated(question, System.currentTimeMillis()));

        try {
            // Crop context to manageable size
            List<Message> croppedContext = contextManager.crop(messages, question);

            // Generate answer
            Answer answer = generationService.generate(config, croppedContext, question);

            // Emit question answered event
            Question answeredQuestion = new Question(
                    question.questionId(),
                    question.sessionId(),
                    question.question(),
                    question.impact(),
                    question.recommendation(),
                    QuestionStatus.ANSWERED,
                    question.category(),
                    question.deliveryMode()
            );
            eventBus.publish(QuestionEvents.questionAnswered(answeredQuestion, answer, System.currentTimeMillis()));

            return answer;
        } catch (AnswerAgentException e) {
            // Re-throw without wrapping
            throw e;
        } catch (Exception e) {
            throw new AnswerAgentException("Failed to generate answer", e);
        }
    }

    /**
     * Returns the configuration for this runtime.
     */
    public AnswerAgentConfig config() {
        return config;
    }

    @Override
    public void close() {
        generationService.close();
    }
}
