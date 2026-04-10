package org.specdriven.agent.question;

import org.specdriven.agent.json.JsonReader;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentMap;

/**
 * Receives human replies from Discord interaction callbacks.
 * Validates the webhook signature, parses the interaction payload,
 * correlates to the originating session, constructs an {@link Answer}
 * with {@code source == HUMAN_MOBILE}, and submits to the {@link QuestionRuntime}.
 */
public class DiscordReplyCollector implements QuestionReplyCollector {

    private static final String HMAC_SHA256 = "HmacSHA256";

    private final QuestionRuntime runtime;
    private final String webhookSecret;
    private final ConcurrentMap<String, String> messageMap;

    DiscordReplyCollector(QuestionRuntime runtime,
                          String webhookSecret,
                          String callbackBaseUrl,
                          ConcurrentMap<String, String> messageMap) {
        this.runtime = Objects.requireNonNull(runtime, "runtime");
        this.webhookSecret = Objects.requireNonNull(webhookSecret, "webhookSecret");
        this.messageMap = Objects.requireNonNull(messageMap, "messageMap");
    }

    /**
     * Process an incoming Discord interaction callback payload.
     * Validates the signature, extracts the reply content, and correlates to the session.
     *
     * @param jsonPayload the raw Discord interaction JSON body
     * @param signatureHeader the X-Signature-Ed25519 header value (or equivalent)
     * @throws MobileAdapterException if the signature is invalid or correlation fails
     */
    public void processCallback(String jsonPayload, String signatureHeader) {
        if (!verifySignature(jsonPayload, signatureHeader)) {
            throw new MobileAdapterException("discord", "Invalid webhook signature");
        }

        Map<String, Object> interaction = JsonReader.parseObject(jsonPayload);
        Map<String, Object> messageRef = JsonReader.getMap(interaction, "message_reference");
        if (messageRef.isEmpty()) {
            throw new MobileAdapterException("discord", "Callback has no message_reference");
        }

        String referencedMessageId = JsonReader.getString(messageRef, "message_id");
        if (referencedMessageId == null || referencedMessageId.isEmpty()) {
            throw new MobileAdapterException("discord", "message_reference has no message_id");
        }

        String sessionId = messageMap.get(referencedMessageId);
        if (sessionId == null) {
            throw new MobileAdapterException("discord",
                    "No session mapping for referenced message_id: " + referencedMessageId);
        }

        Map<String, Object> data = JsonReader.getMap(interaction, "data");
        String replyText = JsonReader.getString(data, "content");
        if (replyText == null || replyText.isBlank()) {
            throw new MobileAdapterException("discord", "Reply has no content");
        }

        Question pending = runtime.pendingQuestion(sessionId)
                .orElseThrow(() -> new MobileAdapterException("discord",
                        "No pending question for session: " + sessionId));

        Answer answer = new Answer(
                replyText,
                "Human reply via Discord",
                "discord:user",
                AnswerSource.HUMAN_MOBILE,
                1.0,
                QuestionDecision.ANSWER_ACCEPTED,
                DeliveryMode.PUSH_MOBILE_WAIT_HUMAN,
                "Routed to mobile for human confirmation",
                System.currentTimeMillis()
        );

        collect(sessionId, pending.questionId(), answer);
    }

    @Override
    public void collect(String sessionId, String questionId, Answer answer) {
        runtime.submitAnswer(sessionId, questionId, answer);
    }

    @Override
    public void close() {
        messageMap.clear();
    }

    private boolean verifySignature(String payload, String signature) {
        try {
            Mac mac = Mac.getInstance(HMAC_SHA256);
            mac.init(new SecretKeySpec(webhookSecret.getBytes(StandardCharsets.UTF_8), HMAC_SHA256));
            byte[] computed = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
            String expected = HexFormat.of().formatHex(computed);
            return expected.equals(signature);
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            throw new MobileAdapterException("discord", "Signature verification failed", e);
        }
    }
}
