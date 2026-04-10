package org.specdriven.agent.question;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.specdriven.agent.event.SimpleEventBus;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import static org.junit.jupiter.api.Assertions.*;

class DiscordReplyCollectorTest {

    private QuestionRuntime runtime;
    private ConcurrentMap<String, String> messageMap;
    private DiscordReplyCollector collector;
    private static final String WEBHOOK_SECRET = "test-secret-key";

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
        collector = new DiscordReplyCollector(runtime, WEBHOOK_SECRET, "http://localhost:8080/callback", messageMap);
    }

    private String computeSignature(String payload) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(WEBHOOK_SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        byte[] hash = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
        return HexFormat.of().formatHex(hash);
    }

    @Test
    void processCallbackWithValidReply() throws Exception {
        Question question = createWaitingQuestion("s-1", "q-1");
        runtime.beginWaitingQuestion(question);
        messageMap.put("msg-42", "s-1");

        String payload = "{\"id\":\"int-1\",\"message_reference\":{\"message_id\":\"msg-42\"}," +
                "\"data\":{\"content\":\"Use option B\"}}";
        String signature = computeSignature(payload);

        collector.processCallback(payload, signature);

        // Verify answer was queued via runtime
        var answerOpt = runtime.pollAnswer("s-1", "q-1", 100);
        assertTrue(answerOpt.isPresent(), "Answer should have been submitted to the runtime");
        assertEquals("Use option B", answerOpt.get().content());
        assertEquals(AnswerSource.HUMAN_MOBILE, answerOpt.get().source());
        assertEquals(DeliveryMode.PUSH_MOBILE_WAIT_HUMAN, answerOpt.get().deliveryMode());
    }

    @Test
    void processCallbackRejectsInvalidSignature() {
        String payload = "{\"id\":\"int-1\",\"message_reference\":{\"message_id\":\"msg-42\"}," +
                "\"data\":{\"content\":\"reply\"}}";

        MobileAdapterException ex = assertThrows(MobileAdapterException.class,
                () -> collector.processCallback(payload, "invalid-signature"));
        assertEquals("discord", ex.channelType());
        assertTrue(ex.getMessage().contains("signature"));
    }

    @Test
    void processCallbackRejectsMissingMessageReference() throws Exception {
        String payload = "{\"id\":\"int-1\",\"data\":{\"content\":\"reply\"}}";
        String signature = computeSignature(payload);

        MobileAdapterException ex = assertThrows(MobileAdapterException.class,
                () -> collector.processCallback(payload, signature));
        assertEquals("discord", ex.channelType());
        assertTrue(ex.getMessage().contains("message_reference"));
    }

    @Test
    void processCallbackRejectsUnknownMessageId() throws Exception {
        String payload = "{\"id\":\"int-1\",\"message_reference\":{\"message_id\":\"unknown\"}," +
                "\"data\":{\"content\":\"reply\"}}";
        String signature = computeSignature(payload);

        MobileAdapterException ex = assertThrows(MobileAdapterException.class,
                () -> collector.processCallback(payload, signature));
        assertEquals("discord", ex.channelType());
        assertTrue(ex.getMessage().contains("No session mapping"));
    }

    @Test
    void collectValidatesAndForwards() {
        Question question = createWaitingQuestion("s-1", "q-1");
        runtime.beginWaitingQuestion(question);

        Answer answer = new Answer(
                "reply text", "test basis", "discord:user",
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
        messageMap.put("msg-1", "session");
        assertDoesNotThrow(collector::close);
        assertTrue(messageMap.isEmpty());
    }
}
