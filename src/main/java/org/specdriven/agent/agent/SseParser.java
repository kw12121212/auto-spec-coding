package org.specdriven.agent.agent;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Parses Server-Sent Events (SSE) from an InputStream.
 * Shared by OpenAI and Claude streaming implementations.
 */
public final class SseParser {

    /**
     * A single SSE event with optional event type and data.
     */
    public record SseEvent(String eventType, String data) {
    }

    private SseParser() {
    }

    /**
     * Reads all SSE events from the given input stream.
     *
     * @param in the input stream containing SSE data
     * @return list of parsed events in order
     */
    public static List<SseEvent> parseAll(InputStream in) throws IOException {
        List<SseEvent> events = new ArrayList<>();
        parse(in, events::add);
        return events;
    }

    /**
     * Reads SSE events from the given input stream and delivers them to the consumer as they arrive.
     *
     * @param in       the input stream containing SSE data
     * @param consumer called once per parsed event
     */
    public static void parse(InputStream in, Consumer<SseEvent> consumer) throws IOException {
        BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
        String eventType = null;
        StringBuilder dataBuffer = new StringBuilder();

        String line;
        while ((line = reader.readLine()) != null) {
            if (line.isEmpty()) {
                // Blank line = event boundary — emit if we have data
                if (dataBuffer.length() > 0) {
                    consumer.accept(new SseEvent(
                            eventType != null ? eventType : "message",
                            dataBuffer.toString()));
                }
                eventType = null;
                dataBuffer.setLength(0);
                continue;
            }

            if (line.startsWith(":")) {
                // Comment line — skip
                continue;
            }

            if (line.startsWith("event:")) {
                eventType = line.substring(6).trim();
            } else if (line.startsWith("data:")) {
                String data = line.substring(5);
                // SSE spec: single optional space after colon
                if (!data.isEmpty() && data.charAt(0) == ' ') {
                    data = data.substring(1);
                }
                // Skip empty data lines
                if (data.isEmpty()) {
                    continue;
                }
                if (dataBuffer.length() > 0) {
                    dataBuffer.append('\n');
                }
                dataBuffer.append(data);
            }
        }

        // Handle last event if stream doesn't end with a blank line
        if (dataBuffer.length() > 0) {
            consumer.accept(new SseEvent(
                    eventType != null ? eventType : "message",
                    dataBuffer.toString()));
        }
    }
}
