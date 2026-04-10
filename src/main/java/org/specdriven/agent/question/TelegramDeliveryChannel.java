package org.specdriven.agent.question;

import org.specdriven.agent.json.JsonReader;
import org.specdriven.agent.json.JsonWriter;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentMap;

/**
 * Sends question notifications to a Telegram chat via the Bot API {@code sendMessage} endpoint.
 * After a successful send, registers the Telegram message_id with the shared message map
 * so that inbound replies can be correlated back to the originating session.
 */
public class TelegramDeliveryChannel implements QuestionDeliveryChannel {

    static final String DEFAULT_BASE_URL = "https://api.telegram.org/bot";

    private final String baseUrl;
    private final String botToken;
    private final String chatId;
    private final HttpClient httpClient;
    private final RichMessageFormatter formatter;
    private final ConcurrentMap<Long, String> messageMap;

    TelegramDeliveryChannel(String botToken,
                            String chatId,
                            HttpClient httpClient,
                            ConcurrentMap<Long, String> messageMap) {
        this(DEFAULT_BASE_URL, botToken, chatId, httpClient, messageMap);
    }

    TelegramDeliveryChannel(String baseUrl,
                            String botToken,
                            String chatId,
                            HttpClient httpClient,
                            ConcurrentMap<Long, String> messageMap) {
        this.baseUrl = baseUrl;
        this.botToken = botToken;
        this.chatId = chatId;
        this.httpClient = httpClient;
        this.messageMap = messageMap;
        this.formatter = PlainTextFormatter.INSTANCE;
    }

    @Override
    public void send(Question question) {
        String text = formatter.format(question);
        String url = baseUrl + botToken + "/sendMessage";
        String body = JsonWriter.object()
                .field("chat_id", chatId)
                .field("text", text)
                .build();

        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .timeout(Duration.ofSeconds(30))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new MobileAdapterException("telegram",
                        "Telegram API error: HTTP " + response.statusCode() + " - " + response.body());
            }

            Map<String, Object> resp = JsonReader.parseObject(response.body());
            Map<String, Object> result = JsonReader.getMap(resp, "result");
            long messageId = JsonReader.getLong(result, "message_id");
            if (messageId > 0) {
                messageMap.put(messageId, question.sessionId());
            }
        } catch (MobileAdapterException e) {
            throw e;
        } catch (Exception e) {
            throw new MobileAdapterException("telegram", "Failed to send question to Telegram", e);
        }
    }

    @Override
    public void close() {
        // HttpClient is shared; nothing to release here
    }
}
