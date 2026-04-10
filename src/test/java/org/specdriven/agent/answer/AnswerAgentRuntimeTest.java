package org.specdriven.agent.answer;

import org.junit.jupiter.api.Test;
import org.specdriven.agent.agent.*;
import org.specdriven.agent.event.Event;
import org.specdriven.agent.event.EventBus;
import org.specdriven.agent.event.EventType;
import org.specdriven.agent.question.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.*;

class AnswerAgentRuntimeTest {

    private Question createTestQuestion() {
        return new Question(
                "q-123",
                "session-456",
                "What is the best approach?",
                "Choosing wrong approach may delay the project",
                "Consider using the Strategy pattern",
                QuestionStatus.OPEN,
                QuestionCategory.PLAN_SELECTION,
                DeliveryMode.AUTO_AI_REPLY
        );
    }

    private LlmProviderRegistry createMockRegistry() {
        return new LlmProviderRegistry() {
            @Override
            public void register(String name, LlmProvider provider) {}

            @Override
            public LlmProvider provider(String name) {
                return new LlmProvider() {
                    @Override
                    public LlmConfig config() {
                        return new LlmConfig("http://test", "key", "model", 30, 0);
                    }

                    @Override
                    public LlmClient createClient() {
                        return request -> new LlmResponse.TextResponse(
                                "This is a test answer",
                                new LlmUsage(10, 20, 30),
                                "stop"
                        );
                    }

                    @Override
                    public void close() {}
                };
            }

            @Override
            public LlmProvider defaultProvider() {
                return provider("test");
            }

            @Override
            public Set<String> providerNames() {
                return Set.of("test");
            }

            @Override
            public void remove(String name) {}

            @Override
            public void setDefault(String name) {}

            @Override
            public SkillRoute route(String skillName) {
                return null;
            }

            @Override
            public void addSkillRoute(String skillName, SkillRoute route) {}

            @Override
            public void close() {}
        };
    }

    private EventBus createCapturingEventBus(List<Event> capturedEvents) {
        return new EventBus() {
            @Override
            public void publish(Event event) {
                capturedEvents.add(event);
            }

            @Override
            public void subscribe(EventType type, Consumer<Event> listener) {}

            @Override
            public void unsubscribe(EventType type, Consumer<Event> listener) {}
        };
    }

    @Test
    void testResolveSuccess() {
        List<Event> events = new CopyOnWriteArrayList<>();
        AnswerAgentConfig config = AnswerAgentConfig.openAiMiniDefaults();
        LlmProviderRegistry registry = createMockRegistry();
        EventBus eventBus = createCapturingEventBus(events);

        AnswerAgentRuntime runtime = new AnswerAgentRuntime(config, registry, eventBus);
        Question question = createTestQuestion();
        List<Message> messages = List.of(new UserMessage("Test context", 1000));

        Answer answer = runtime.resolve(question, messages);

        assertNotNull(answer);
        assertEquals("This is a test answer", answer.content());
        assertEquals(AnswerSource.AI_AGENT, answer.source());
        assertEquals(QuestionDecision.ANSWER_ACCEPTED, answer.decision());
        assertEquals(DeliveryMode.AUTO_AI_REPLY, answer.deliveryMode());
        assertEquals(0.9, answer.confidence());
        assertNotNull(answer.basisSummary());
        assertNotNull(answer.sourceRef());
        assertTrue(answer.answeredAt() > 0);

        // Verify events were emitted
        assertEquals(2, events.size());
        assertEquals(EventType.QUESTION_CREATED, events.get(0).type());
        assertEquals(EventType.QUESTION_ANSWERED, events.get(1).type());
    }

    @Test
    void testResolveWithWaitingStatus() {
        List<Event> events = new ArrayList<>();
        AnswerAgentConfig config = AnswerAgentConfig.openAiMiniDefaults();
        LlmProviderRegistry registry = createMockRegistry();
        EventBus eventBus = createCapturingEventBus(events);

        AnswerAgentRuntime runtime = new AnswerAgentRuntime(config, registry, eventBus);
        Question question = new Question(
                "q-123",
                "session-456",
                "What?",
                "Impact",
                "Recommendation",
                QuestionStatus.WAITING_FOR_ANSWER, // Different status
                QuestionCategory.CLARIFICATION,
                DeliveryMode.AUTO_AI_REPLY
        );
        List<Message> messages = List.of();

        Answer answer = runtime.resolve(question, messages);

        assertNotNull(answer);
        assertEquals(AnswerSource.AI_AGENT, answer.source());
    }

    @Test
    void testResolveWithInvalidStatusThrows() {
        AnswerAgentConfig config = AnswerAgentConfig.openAiMiniDefaults();
        LlmProviderRegistry registry = createMockRegistry();
        EventBus eventBus = createCapturingEventBus(new ArrayList<>());

        AnswerAgentRuntime runtime = new AnswerAgentRuntime(config, registry, eventBus);
        Question question = new Question(
                "q-123",
                "session-456",
                "What?",
                "Impact",
                "Recommendation",
                QuestionStatus.ANSWERED, // Invalid status
                QuestionCategory.CLARIFICATION,
                DeliveryMode.AUTO_AI_REPLY
        );
        List<Message> messages = List.of();

        assertThrows(AnswerAgentException.class, () -> runtime.resolve(question, messages));
    }

    @Test
    void testNullQuestionThrows() {
        EventBus eventBus = createCapturingEventBus(new ArrayList<>());
        AnswerAgentRuntime runtime = new AnswerAgentRuntime(
                AnswerAgentConfig.openAiMiniDefaults(),
                createMockRegistry(),
                eventBus
        );

        assertThrows(NullPointerException.class, () -> runtime.resolve(null, List.of()));
    }

    @Test
    void testNullMessagesThrows() {
        EventBus eventBus = createCapturingEventBus(new ArrayList<>());
        AnswerAgentRuntime runtime = new AnswerAgentRuntime(
                AnswerAgentConfig.openAiMiniDefaults(),
                createMockRegistry(),
                eventBus
        );

        assertThrows(NullPointerException.class, () -> runtime.resolve(createTestQuestion(), null));
    }

    @Test
    void testDefaultConfigConstructor() {
        EventBus eventBus = createCapturingEventBus(new ArrayList<>());
        LlmProviderRegistry registry = createMockRegistry();

        AnswerAgentRuntime runtime = new AnswerAgentRuntime(registry, eventBus);

        assertEquals("openai", runtime.config().providerName());
        assertEquals("gpt-4o-mini", runtime.config().model());
    }

    @Test
    void testClose() {
        EventBus eventBus = createCapturingEventBus(new ArrayList<>());
        AnswerAgentRuntime runtime = new AnswerAgentRuntime(
                AnswerAgentConfig.openAiMiniDefaults(),
                createMockRegistry(),
                eventBus
        );

        assertDoesNotThrow(() -> runtime.close());
    }
}
