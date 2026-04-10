package org.specdriven.agent.question;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.http.HttpClient;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import static org.junit.jupiter.api.Assertions.*;

class DiscordDeliveryChannelTest {

    private HttpServer server;
    private ConcurrentMap<String, String> messageMap;

    private static Question sampleQuestion() {
        return new Question(
                "q-1", "s-1",
                "Which approach?", "Wrong choice delays delivery",
                "Use option A",
                QuestionStatus.WAITING_FOR_ANSWER,
                QuestionCategory.PLAN_SELECTION,
                DeliveryMode.PUSH_MOBILE_WAIT_HUMAN
        );
    }

    @BeforeEach
    void setUp() throws IOException {
        messageMap = new ConcurrentHashMap<>();
        server = HttpServer.create(new InetSocketAddress(0), 0);
    }

    @AfterEach
    void tearDown() {
        if (server != null) server.stop(0);
    }

    private void startServer(int statusCode, String responseBody) {
        server.createContext("/", exchange -> {
            byte[] body = responseBody.getBytes();
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(statusCode, body.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(body);
            }
        });
        server.start();
    }

    @Test
    void sendSuccessRegistersMessageId() {
        startServer(200, "{\"id\":\"msg-99\",\"type\":0}");

        String webhookUrl = "http://localhost:" + server.getAddress().getPort() + "/webhook";
        DiscordDeliveryChannel channel = new DiscordDeliveryChannel(
                webhookUrl, HttpClient.newHttpClient(), messageMap);

        channel.send(sampleQuestion());
        assertEquals("s-1", messageMap.get("msg-99"));
    }

    @Test
    void webhookErrorThrowsMobileAdapterException() {
        startServer(401, "{\"message\": \"Unauthorized\"}");

        String webhookUrl = "http://localhost:" + server.getAddress().getPort() + "/webhook";
        DiscordDeliveryChannel channel = new DiscordDeliveryChannel(
                webhookUrl, HttpClient.newHttpClient(), messageMap);

        MobileAdapterException ex = assertThrows(MobileAdapterException.class,
                () -> channel.send(sampleQuestion()));
        assertEquals("discord", ex.channelType());
        assertTrue(ex.getMessage().contains("401"));
    }

    @Test
    void connectionErrorThrowsMobileAdapterException() {
        DiscordDeliveryChannel channel = new DiscordDeliveryChannel(
                "http://localhost:1/webhook", HttpClient.newHttpClient(), messageMap);

        MobileAdapterException ex = assertThrows(MobileAdapterException.class,
                () -> channel.send(sampleQuestion()));
        assertEquals("discord", ex.channelType());
    }

    @Test
    void closeDoesNotThrow() {
        DiscordDeliveryChannel channel = new DiscordDeliveryChannel(
                "http://localhost:1/webhook", HttpClient.newHttpClient(), messageMap);
        assertDoesNotThrow(channel::close);
    }
}
