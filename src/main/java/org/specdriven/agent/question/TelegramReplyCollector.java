package org.specdriven.agent.question;

import org.specdriven.agent.json.JsonReader;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentMap;

/**
 * Receives human replies from Telegram webhook callbacks.
 * Parses inbound Telegram {@code message} updates, correlates them to the originating
 * session via the shared message_id map, constructs an {@link Answer} with
 * {@code source == HUMAN_MOBILE}, and submits to the {@link QuestionRuntime}.
 */
public class TelegramReplyCollector implements QuestionReplyCollector {

    private final QuestionRuntime runtime;
    private final ConcurrentMap<Long, String> messageMap;

    TelegramReplyCollector(QuestionRuntime runtime,
                           String botToken,
                           String callbackBaseUrl,
                           ConcurrentMap<Long, String> messageMap) {
        this.runtime = Objects.requireNonNull(runtime, "runtime");
        this.messageMap = Objects.requireNonNull(messageMap, "messageMap");
    }

    /**
     * Process an incoming Telegram Update webhook payload.
     * Extracts the reply text and correlates it to the originating session.
     *
     * @param jsonPayload the raw Telegram Update JSON
     * @throws MobileAdapterException if the payload is invalid or correlation fails
     */
    public void processCallback(String jsonPayload) {
        Map<String, Object> update = JsonReader.parseObject(jsonPayload);
        Map<String, Object> message = JsonReader.getMap(update, "message");
        if (message.isEmpty()) {
            throw new MobileAdapterException("telegram", "Callback has no message field");
        }

        Map<String, Object> replyTo = JsonReader.getMap(message, "reply_to_message");
        if (replyTo.isEmpty()) {
            throw new MobileAdapterException("telegram", "Callback message is not a reply");
        }

        long repliedToId = JsonReader.getLong(replyTo, "message_id");
        String sessionId = messageMap.get(repliedToId);
        if (sessionId == null) {
            throw new MobileAdapterException("telegram",
                    "No session mapping for replied-to message_id: " + repliedToId);
        }

        String replyText = JsonReader.getString(message, "text");
        if (replyText == null || replyText.isBlank()) {
            throw new MobileAdapterException("telegram", "Reply has no text content");
        }

        Question pending = runtime.pendingQuestion(sessionId)
                .orElseThrow(() -> new MobileAdapterException("telegram",
                        "No pending question for session: " + sessionId));

        Answer answer = new Answer(
                replyText,
                "Human reply via Telegram",
                "telegram:user",
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
}
