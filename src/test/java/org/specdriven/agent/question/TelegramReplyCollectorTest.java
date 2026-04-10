package org.specdriven.agent.question;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.specdriven.agent.event.SimpleEventBus;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import static org.junit.jupiter.api.Assertions.*;

class TelegramReplyCollectorTest {

    private QuestionRuntime runtime;
    private ConcurrentMap<Long, String> messageMap;
    private TelegramReplyCollector collector;

    private static Question createWaitingQuestion(String sessionId, String questionId) {
        return new Question(
                questionId, sessionId,
                "Which approach?", "Wrong choice delays delivery",
                "Use option A",
                QuestionStatus.WAITING_FOR_ANSWER,
                QuestionCategory.PLAN_SELECTION,
                DeliveryMode.PUSH_MOBILE_WAIT_HUMAN
        );
    }

    @BeforeEach
    void setUp() {
        runtime = new QuestionRuntime(new SimpleEventBus());
        messageMap = new ConcurrentHashMap<>();
        collector = new TelegramReplyCollector(runtime, "testBotToken", "http://localhost:8080/callback", messageMap);
    }

    @Test
    void processCallbackWithValidReply() throws Exception {
        Question question = createWaitingQuestion("s-1", "q-1");
        runtime.beginWaitingQuestion(question);
        messageMap.put(42L, "s-1");

        String payload = "{\"update_id\":100,\"message\":{\"message_id\":200," +
                "\"reply_to_message\":{\"message_id\":42}," +
                "\"text\":\"Use option B\",\"from\":{\"id\":111}}}";

        collector.processCallback(payload);

        // Verify answer was queued via runtime
        var answerOpt = runtime.pollAnswer("s-1", "q-1", 100);
        assertTrue(answerOpt.isPresent(), "Answer should have been submitted to the runtime");
        assertEquals("Use option B", answerOpt.get().content());
        assertEquals(AnswerSource.HUMAN_MOBILE, answerOpt.get().source());
        assertEquals(DeliveryMode.PUSH_MOBILE_WAIT_HUMAN, answerOpt.get().deliveryMode());
    }

    @Test
    void processCallbackRejectsMissingMessageField() {
        String payload = "{\"update_id\":100}";

        MobileAdapterException ex = assertThrows(MobileAdapterException.class,
                () -> collector.processCallback(payload));
        assertEquals("telegram", ex.channelType());
        assertTrue(ex.getMessage().contains("no message"));
    }

    @Test
    void processCallbackRejectsNonReplyMessage() {
        String payload = "{\"message\":{\"message_id\":200,\"text\":\"hello\"}}";

        MobileAdapterException ex = assertThrows(MobileAdapterException.class,
                () -> collector.processCallback(payload));
        assertEquals("telegram", ex.channelType());
        assertTrue(ex.getMessage().contains("not a reply"));
    }

    @Test
    void processCallbackRejectsUnknownMessageId() {
        String payload = "{\"message\":{\"message_id\":200," +
                "\"reply_to_message\":{\"message_id\":999}," +
                "\"text\":\"reply\"}}";

        MobileAdapterException ex = assertThrows(MobileAdapterException.class,
                () -> collector.processCallback(payload));
        assertEquals("telegram", ex.channelType());
        assertTrue(ex.getMessage().contains("No session mapping"));
    }

    @Test
    void collectValidatesAndForwards() {
        Question question = createWaitingQuestion("s-1", "q-1");
        runtime.beginWaitingQuestion(question);

        Answer answer = new Answer(
                "reply text", "test basis", "telegram:user",
                AnswerSource.HUMAN_MOBILE, 1.0,
                QuestionDecision.ANSWER_ACCEPTED,
                DeliveryMode.PUSH_MOBILE_WAIT_HUMAN,
                "test escalation",
                System.currentTimeMillis()
        );

        assertDoesNotThrow(() -> collector.collect("s-1", "q-1", answer));
    }

    @Test
    void closeDoesNotThrow() {
        messageMap.put(1L, "session");
        assertDoesNotThrow(collector::close);
        assertTrue(messageMap.isEmpty());
    }
}
