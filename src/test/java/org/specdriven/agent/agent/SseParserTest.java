package org.specdriven.agent.agent;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class SseParserTest {

    private List<SseParser.SseEvent> parse(String sseText) throws IOException {
        ByteArrayInputStream in = new ByteArrayInputStream(sseText.getBytes(StandardCharsets.UTF_8));
        return SseParser.parseAll(in);
    }

    @Test
    void parseSingleDataLine() throws IOException {
        List<SseParser.SseEvent> events = parse("data: {\"content\":\"hello\"}\n\n");
        assertEquals(1, events.size());
        assertEquals("{\"content\":\"hello\"}", events.get(0).data());
    }

    @Test
    void skipCommentLines() throws IOException {
        List<SseParser.SseEvent> events = parse(": this is a comment\ndata: {\"content\":\"x\"}\n\n");
        assertEquals(1, events.size());
        assertEquals("{\"content\":\"x\"}", events.get(0).data());
    }

    @Test
    void skipEmptyDataLine() throws IOException {
        List<SseParser.SseEvent> events = parse("data: \n\n");
        assertEquals(0, events.size());
    }

    @Test
    void parseEventWithData() throws IOException {
        String sse = "event: message_start\ndata: {\"type\":\"message_start\"}\n\n";
        List<SseParser.SseEvent> events = parse(sse);
        assertEquals(1, events.size());
        assertEquals("message_start", events.get(0).eventType());
        assertEquals("{\"type\":\"message_start\"}", events.get(0).data());
    }

    @Test
    void parseMultipleEvents() throws IOException {
        String sse = "data: first\n\ndata: second\n\n";
        List<SseParser.SseEvent> events = parse(sse);
        assertEquals(2, events.size());
        assertEquals("first", events.get(0).data());
        assertEquals("second", events.get(1).data());
    }

    @Test
    void parseOpenAiDone() throws IOException {
        String sse = "data: {\"choices\":[{\"delta\":{\"content\":\"hi\"}}]}\n\ndata: [DONE]\n\n";
        List<SseParser.SseEvent> events = parse(sse);
        assertEquals(2, events.size());
        assertEquals("[DONE]", events.get(1).data());
    }

    @Test
    void dataWithoutSpaceAfterColon() throws IOException {
        List<SseParser.SseEvent> events = parse("data:{\"key\":\"value\"}\n\n");
        assertEquals(1, events.size());
        assertEquals("{\"key\":\"value\"}", events.get(0).data());
    }

    @Test
    void defaultEventTypeIsMessage() throws IOException {
        List<SseParser.SseEvent> events = parse("data: hello\n\n");
        assertEquals("message", events.get(0).eventType());
    }
}
