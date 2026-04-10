package org.specdriven.agent.question;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.specdriven.agent.event.SimpleEventBus;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import static org.junit.jupiter.api.Assertions.*;

class ReplyCallbackRouterTest {

    private ReplyCallbackRouter router;
    private QuestionRuntime runtime;

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
        router = new ReplyCallbackRouter();
        runtime = new QuestionRuntime(new SimpleEventBus());
    }

    // --- Registration ---

    @Test
    void registerAddsChannelType() {
        TelegramReplyCollector collector = createTelegramCollector();
        router.register("telegram", collector, "my-secret");

        assertTrue(router.registeredChannels().contains("telegram"));
    }

    @Test
    void registerRejectsDuplicateChannelType() {
        TelegramReplyCollector collector = createTelegramCollector();
        router.register("telegram", collector, "secret");

        TelegramReplyCollector duplicate = createTelegramCollector();
        assertThrows(IllegalArgumentException.class,
                () -> router.register("telegram", duplicate, "other"));
    }

    @Test
    void registeredChannelsReturnsAllTypes() {
        TelegramReplyCollector telegram = createTelegramCollector();
        DiscordReplyCollector discord = createDiscordCollector();
        router.register("telegram", telegram, "secret");
        router.register("discord", discord, null);

        assertEquals(2, router.registeredChannels().size());
        assertTrue(router.registeredChannels().contains("telegram"));
        assertTrue(router.registeredChannels().contains("discord"));
    }

    // --- Telegram dispatch ---

    @Test
    void dispatchTelegramWithValidSecret() throws Exception {
        ConcurrentMap<Long, String> messageMap = new ConcurrentHashMap<>();
        TelegramReplyCollector collector = new TelegramReplyCollector(
                runtime, "token", "http://localhost/callback", messageMap);
        router.register("telegram", collector, "expected-secret");

        Question question = createWaitingQuestion("s-1", "q-1");
        runtime.beginWaitingQuestion(question);
        messageMap.put(42L, "s-1");

        String payload = "{\"update_id\":1,\"message\":{\"message_id\":200," +
                "\"reply_to_message\":{\"message_id\":42}," +
                "\"text\":\"Use B\",\"from\":{\"id\":1}}}";
        Map<String, String> headers = Map.of("X-Telegram-Bot-Api-Secret-Token", "expected-secret");

        assertDoesNotThrow(() -> router.dispatch("telegram", payload, headers));

        var answer = runtime.pollAnswer("s-1", "q-1", 100);
        assertTrue(answer.isPresent());
        assertEquals("Use B", answer.get().content());
    }

    @Test
    void dispatchTelegramRejectsMismatchedSecret() throws Exception {
        TelegramReplyCollector collector = createTelegramCollector();
        router.register("telegram", collector, "expected-secret");

        Map<String, String> headers = Map.of("X-Telegram-Bot-Api-Secret-Token", "wrong-secret");

        MobileAdapterException ex = assertThrows(MobileAdapterException.class,
                () -> router.dispatch("telegram", "{}", headers));
        assertEquals("telegram", ex.channelType());
    }

    @Test
    void dispatchTelegramRejectsMissingSecret() {
        TelegramReplyCollector collector = createTelegramCollector();
        router.register("telegram", collector, "expected-secret");

        Map<String, String> headers = Map.of();

        MobileAdapterException ex = assertThrows(MobileAdapterException.class,
                () -> router.dispatch("telegram", "{}", headers));
        assertEquals("telegram", ex.channelType());
    }

    @Test
    void dispatchTelegramSkipsVerificationWhenNoSecretConfigured() {
        ConcurrentMap<Long, String> messageMap = new ConcurrentHashMap<>();
        TelegramReplyCollector collector = new TelegramReplyCollector(
                runtime, "token", "http://localhost/callback", messageMap);
        router.register("telegram", collector, null);

        Question question = createWaitingQuestion("s-1", "q-1");
        runtime.beginWaitingQuestion(question);
        messageMap.put(42L, "s-1");

        String payload = "{\"update_id\":1,\"message\":{\"message_id\":200," +
                "\"reply_to_message\":{\"message_id\":42}," +
                "\"text\":\"reply\",\"from\":{\"id\":1}}}";

        assertDoesNotThrow(() -> router.dispatch("telegram", payload, Map.of()));
    }

    // --- Discord dispatch ---

    @Test
    void dispatchDiscordForwardsSignature() throws Exception {
        ConcurrentMap<String, String> messageMap = new ConcurrentHashMap<>();
        String webhookSecret = "discord-secret";
        DiscordReplyCollector collector = new DiscordReplyCollector(
                runtime, webhookSecret, "http://localhost/callback", messageMap);
        router.register("discord", collector, null);

        Question question = createWaitingQuestion("s-1", "q-1");
        runtime.beginWaitingQuestion(question);
        messageMap.put("msg-42", "s-1");

        String payload = "{\"id\":\"int-1\",\"message_reference\":{\"message_id\":\"msg-42\"}," +
                "\"data\":{\"content\":\"Use B\"}}";
        String signature = computeHmac(payload, webhookSecret);
        Map<String, String> headers = Map.of("X-Signature-256", signature);

        assertDoesNotThrow(() -> router.dispatch("discord", payload, headers));

        var answer = runtime.pollAnswer("s-1", "q-1", 100);
        assertTrue(answer.isPresent());
        assertEquals("Use B", answer.get().content());
    }

    // --- Unknown channel type ---

    @Test
    void dispatchRejectsUnknownChannelType() {
        assertThrows(IllegalArgumentException.class,
                () -> router.dispatch("slack", "{}", Map.of()));
    }

    // --- Helpers ---

    private TelegramReplyCollector createTelegramCollector() {
        return new TelegramReplyCollector(runtime, "token", "http://localhost/callback",
                new ConcurrentHashMap<>());
    }

    private DiscordReplyCollector createDiscordCollector() {
        return new DiscordReplyCollector(runtime, "secret", "http://localhost/callback",
                new ConcurrentHashMap<>());
    }

    private String computeHmac(String payload, String secret) throws Exception {
        javax.crypto.Mac mac = javax.crypto.Mac.getInstance("HmacSHA256");
        mac.init(new javax.crypto.spec.SecretKeySpec(secret.getBytes(java.nio.charset.StandardCharsets.UTF_8), "HmacSHA256"));
        byte[] hash = mac.doFinal(payload.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        return java.util.HexFormat.of().formatHex(hash);
    }
}
