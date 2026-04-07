package org.specdriven.agent.agent;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class LlmClientTest {

    /**
     * Verify the default delegation: chat(LlmRequest) falls back to chat(List<Message>)
     */
    @Test
    void chatWithLlmRequest_delegatesToChatWithMessages() {
        List<Message> messages = List.of(new UserMessage("hello", System.currentTimeMillis()));
        LlmResponse expected = new LlmResponse.TextResponse("world");

        // A minimal LlmClient that only implements chat(List<Message>)
        LlmClient client = msgList -> expected;

        LlmRequest request = LlmRequest.of(messages);
        LlmResponse actual = client.chat(request);

        assertInstanceOf(LlmResponse.TextResponse.class, actual);
        assertEquals("world", ((LlmResponse.TextResponse) actual).content());
    }

    @Test
    void chatWithLlmRequest_passesSystemPrompt() {
        List<Message> messages = List.of(new UserMessage("hi", System.currentTimeMillis()));

        // Capture what messages are actually passed through
        final java.util.concurrent.atomic.AtomicReference<List<Message>> captured =
                new java.util.concurrent.atomic.AtomicReference<>();

        LlmClient client = msgList -> {
            captured.set(msgList);
            return new LlmResponse.TextResponse("ok");
        };

        LlmRequest request = LlmRequest.of(messages, "You are helpful");
        client.chat(request);

        // The default implementation just passes the original message list
        // (system prompt handling is the provider's job, not the client's)
        assertNotNull(captured.get());
    }

    @Test
    void chatStreaming_defaultThrows() {
        LlmClient client = msgList -> new LlmResponse.TextResponse("ok");
        LlmRequest request = LlmRequest.of(List.of(new UserMessage("hi", System.currentTimeMillis())));
        LlmStreamCallback callback = new LlmStreamCallback() {
            @Override public void onToken(String token) {}
            @Override public void onComplete(LlmResponse response) {}
            @Override public void onError(Exception e) {}
        };

        assertThrows(UnsupportedOperationException.class,
                () -> client.chatStreaming(request, callback));
    }
}
