package org.specdriven.agent.testsupport;

import org.specdriven.agent.json.JsonReader;
import org.specdriven.agent.json.JsonWriter;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;

final class JsonRpcStdio {

    private JsonRpcStdio() {
    }

    static Map<String, Object> readMessage(InputStream input) throws IOException {
        int contentLength = readContentLength(input);
        if (contentLength <= 0) {
            return null;
        }

        byte[] body = input.readNBytes(contentLength);
        if (body.length < contentLength) {
            return null;
        }
        return JsonReader.parseObject(new String(body, StandardCharsets.UTF_8));
    }

    static void writeMessage(OutputStream output, Map<String, Object> message) throws IOException {
        byte[] body = JsonWriter.fromMap(message).getBytes(StandardCharsets.UTF_8);
        output.write(("Content-Length: " + body.length + "\r\n\r\n").getBytes(StandardCharsets.UTF_8));
        output.write(body);
        output.flush();
    }

    private static int readContentLength(InputStream input) throws IOException {
        String line;
        while ((line = readHeaderLine(input)) != null) {
            if (line.isEmpty()) {
                continue;
            }
            if (line.startsWith("Content-Length:")) {
                int contentLength = Integer.parseInt(line.substring(15).trim());
                while ((line = readHeaderLine(input)) != null && !line.isEmpty()) {
                }
                return contentLength;
            }
        }
        return -1;
    }

    private static String readHeaderLine(InputStream input) throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        int previous = -1;
        while (true) {
            int current = input.read();
            if (current < 0) {
                return null;
            }
            if (previous == '\r' && current == '\n') {
                byte[] bytes = buffer.toByteArray();
                return new String(bytes, 0, bytes.length - 1, StandardCharsets.UTF_8);
            }
            buffer.write(current);
            previous = current;
        }
    }
}
