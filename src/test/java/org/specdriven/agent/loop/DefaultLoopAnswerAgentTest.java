package org.specdriven.agent.loop;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.specdriven.agent.agent.LlmClient;
import org.specdriven.agent.agent.LlmResponse;
import org.specdriven.agent.agent.Message;
import org.specdriven.agent.event.SimpleEventBus;
import org.specdriven.agent.question.Answer;
import org.specdriven.agent.question.AnswerSource;
import org.specdriven.agent.question.DeliveryMode;
import org.specdriven.agent.question.Question;
import org.specdriven.agent.question.QuestionCategory;
import org.specdriven.agent.question.QuestionDecision;
import org.specdriven.agent.question.QuestionRuntime;
import org.specdriven.agent.question.QuestionStatus;

class DefaultLoopAnswerAgentTest {

    private static Question sampleQuestion() {
        return new Question(
                "q-1", "session-1",
                "Which approach should I use?",
                "Affects architecture",
                "Use approach A",
                QuestionStatus.WAITING_FOR_ANSWER,
                QuestionCategory.PERMISSION_CONFIRMATION,
                DeliveryMode.PAUSE_WAIT_HUMAN
        );
    }

    private static LlmClient stubLlm(String responseText) {
        return messages -> new LlmResponse.TextResponse(responseText);
    }

    /**
     * QuestionRuntime stub that accepts submitAnswer calls without requiring
     * a pre-registered pending question.
     */
    private static QuestionRuntime acceptingRuntime() {
        return new QuestionRuntime(new SimpleEventBus()) {
            @Override
            public void submitAnswer(String sessionId, String questionId, Answer answer) {
                // Accept without validation
            }
        };
    }

    /**
     * QuestionRuntime stub where submitAnswer always throws.
     */
    private static QuestionRuntime throwingRuntime(String message) {
        return new QuestionRuntime(new SimpleEventBus()) {
            @Override
            public void submitAnswer(String sessionId, String questionId, Answer answer) {
                throw new IllegalStateException(message);
            }
        };
    }

    @Test
    void resolveReturnsResolvedOnSuccessfulLlmCall() {
        LlmClient llm = stubLlm("Use approach A — it is the best fit.");
        LoopAnswerAgent agent = new DefaultLoopAnswerAgent(llm, acceptingRuntime());

        AnswerResolution result = agent.resolve(sampleQuestion(), 30);

        assertInstanceOf(AnswerResolution.Resolved.class, result);
        AnswerResolution.Resolved resolved = (AnswerResolution.Resolved) result;
        assertNotNull(resolved.answer());
        assertEquals(AnswerSource.AI_AGENT, resolved.answer().source());
        assertEquals(QuestionDecision.ANSWER_ACCEPTED, resolved.answer().decision());
        assertEquals(DeliveryMode.AUTO_AI_REPLY, resolved.answer().deliveryMode());
        assertEquals(0.8, resolved.answer().confidence(), 0.001);
        assertEquals("Use approach A — it is the best fit.", resolved.answer().content());
    }

    @Test
    void resolveReturnsEscalatedWhenSubmitAnswerThrows() {
        LlmClient llm = stubLlm("My answer");
        LoopAnswerAgent agent = new DefaultLoopAnswerAgent(llm, throwingRuntime("runtime error"));

        AnswerResolution result = agent.resolve(sampleQuestion(), 30);

        assertInstanceOf(AnswerResolution.Escalated.class, result);
        AnswerResolution.Escalated escalated = (AnswerResolution.Escalated) result;
        assertTrue(escalated.reason().contains("runtime error"));
    }

    @Test
    void resolveReturnsEscalatedOnTimeout() {
        // LlmClient that sleeps longer than the timeout
        LlmClient slowLlm = messages -> {
            try {
                Thread.sleep(10_000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            return new LlmResponse.TextResponse("too late");
        };

        LoopAnswerAgent agent = new DefaultLoopAnswerAgent(slowLlm, acceptingRuntime());
        AnswerResolution result = agent.resolve(sampleQuestion(), 1);

        assertInstanceOf(AnswerResolution.Escalated.class, result);
        assertEquals("timeout", ((AnswerResolution.Escalated) result).reason());
    }

    @Test
    void resolveReturnsEscalatedWhenLlmThrows() {
        LlmClient failingLlm = messages -> {
            throw new RuntimeException("LLM unavailable");
        };

        LoopAnswerAgent agent = new DefaultLoopAnswerAgent(failingLlm, acceptingRuntime());
        AnswerResolution result = agent.resolve(sampleQuestion(), 30);

        assertInstanceOf(AnswerResolution.Escalated.class, result);
        assertTrue(((AnswerResolution.Escalated) result).reason().contains("LLM unavailable"));
    }

    @Test
    void constructorRejectsNullLlmClient() {
        assertThrows(NullPointerException.class,
                () -> new DefaultLoopAnswerAgent(null, acceptingRuntime()));
    }

    @Test
    void constructorRejectsNullQuestionRuntime() {
        assertThrows(NullPointerException.class,
                () -> new DefaultLoopAnswerAgent(stubLlm("ok"), null));
    }

    @Test
    void resolvedAnswerHasNonBlankBasisAndSourceRef() {
        LlmClient llm = stubLlm("Proceed with approach A.");
        LoopAnswerAgent agent = new DefaultLoopAnswerAgent(llm, acceptingRuntime());

        AnswerResolution result = agent.resolve(sampleQuestion(), 30);
        assertInstanceOf(AnswerResolution.Resolved.class, result);
        Answer answer = ((AnswerResolution.Resolved) result).answer();
        assertNotNull(answer.basisSummary());
        assertFalse(answer.basisSummary().isBlank());
        assertNotNull(answer.sourceRef());
        assertFalse(answer.sourceRef().isBlank());
    }
}
