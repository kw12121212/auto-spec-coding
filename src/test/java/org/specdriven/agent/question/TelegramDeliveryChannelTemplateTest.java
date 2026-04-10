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

class TelegramDeliveryChannelTemplateTest {

    private HttpServer server;
    private ConcurrentMap<Long, String> messageMap;

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
    void sendWithTemplateFormatsText() {
        startServer(200, "{\"ok\":true,\"result\":{\"message_id\":42}}");

        String baseUrl = "http://localhost:" + server.getAddress().getPort() + "/bot";
        TelegramDeliveryChannel channel = new TelegramDeliveryChannel(
                baseUrl, "testToken", "chat123",
                HttpClient.newHttpClient(), messageMap, new TelegramMessageTemplate());

        channel.send(sampleQuestion());
        assertEquals("s-1", messageMap.get(42L));
    }

    @Test
    void sendWithPlainTextFormatterStillWorks() {
        startServer(200, "{\"ok\":true,\"result\":{\"message_id\":55}}");

        String baseUrl = "http://localhost:" + server.getAddress().getPort() + "/bot";
        TelegramDeliveryChannel channel = new TelegramDeliveryChannel(
                baseUrl, "testToken", "chat123",
                HttpClient.newHttpClient(), messageMap);

        channel.send(sampleQuestion());
        assertEquals("s-1", messageMap.get(55L));
    }
}
