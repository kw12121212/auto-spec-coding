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
 * Sends question notifications to a Discord channel via a webhook URL.
 * After a successful send, registers the Discord message_id with the shared message map
 * so that inbound replies can be correlated back to the originating session.
 */
public class DiscordDeliveryChannel implements QuestionDeliveryChannel {

    private final String webhookUrl;
    private final HttpClient httpClient;
    private final RichMessageFormatter formatter;
    private final ConcurrentMap<String, String> messageMap;

    DiscordDeliveryChannel(String webhookUrl,
                           HttpClient httpClient,
                           ConcurrentMap<String, String> messageMap) {
        this(webhookUrl, httpClient, messageMap, PlainTextFormatter.INSTANCE);
    }

    DiscordDeliveryChannel(String webhookUrl,
                           HttpClient httpClient,
                           ConcurrentMap<String, String> messageMap,
                           RichMessageFormatter formatter) {
        this.webhookUrl = webhookUrl;
        this.httpClient = httpClient;
        this.messageMap = messageMap;
        this.formatter = formatter;
    }

    @Override
    public void send(Question question) {
        String content = formatter.format(question);
        String body = JsonWriter.object()
                .field("content", content)
                .build();

        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(webhookUrl))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .timeout(Duration.ofSeconds(30))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new MobileAdapterException("discord",
                        "Discord webhook error: HTTP " + response.statusCode() + " - " + response.body());
            }

            Map<String, Object> resp = JsonReader.parseObject(response.body());
            String messageId = JsonReader.getString(resp, "id");
            if (messageId != null && !messageId.isEmpty()) {
                messageMap.put(messageId, question.sessionId());
            }
        } catch (MobileAdapterException e) {
            throw e;
        } catch (Exception e) {
            throw new MobileAdapterException("discord", "Failed to send question to Discord", e);
        }
    }

    @Override
    public void close() {
        // HttpClient is shared; nothing to release here
    }
}
