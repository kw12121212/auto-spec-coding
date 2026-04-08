package org.specdriven.agent.jsonrpc;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Framed JSON-RPC 2.0 transport over {@link InputStream}/{@link OutputStream}
 * using Content-Length header framing (same as LSP protocol).
 *
 * <p>Each message is framed as: {@code Content-Length: <n>\r\n\r\n<n bytes of UTF-8 JSON>}</p>
 *
 * <p>Thread-safe for concurrent {@link #send} calls. Reading runs on a daemon
 * background thread started by {@link #start}.</p>
 */
public class StdioTransport implements JsonRpcTransport {

    private static final int DEFAULT_MAX_MESSAGE_SIZE = 10 * 1024 * 1024; // 10 MB
    private static final String HEADER_PREFIX = "Content-Length: ";
    private static final Pattern CONTENT_LENGTH_PATTERN =
            Pattern.compile("Content-Length:\\s*(\\d+)", Pattern.CASE_INSENSITIVE);

    private final InputStream input;
    private final OutputStream output;
    private final int maxMessageSize;

    private volatile Thread readerThread;
    private volatile JsonRpcMessageHandler handler;
    private volatile boolean running;

    /**
     * Create a transport with default max message size (10 MB).
     */
    public StdioTransport(InputStream input, OutputStream output) {
        this(input, output, DEFAULT_MAX_MESSAGE_SIZE);
    }

    /**
     * Create a transport with a custom max message size.
     *
     * @param maxMessageSize maximum allowed Content-Length in bytes
     */
    public StdioTransport(InputStream input, OutputStream output, int maxMessageSize) {
        this.input = input;
        this.output = output;
        this.maxMessageSize = maxMessageSize;
    }

    @Override
    public void start(JsonRpcMessageHandler handler) {
        if (running) {
            throw new IllegalStateException("Transport is already running");
        }
        this.handler = handler;
        this.running = true;
        readerThread = Thread.ofPlatform().daemon(true).name("jsonrpc-reader").start(this::readLoop);
    }

    @Override
    public void stop() {
        if (!running) {
            return;
        }
        running = false;
        if (readerThread != null) {
            readerThread.interrupt();
            try {
                readerThread.join(5000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            readerThread = null;
        }
    }

    @Override
    public void close() {
        stop();
        try {
            input.close();
        } catch (IOException ignored) {
            // best-effort
        }
        try {
            output.close();
        } catch (IOException ignored) {
            // best-effort
        }
    }

    @Override
    public void send(JsonRpcResponse response) {
        String json = JsonRpcCodec.encode(response);
        writeFrame(json);
    }

    @Override
    public void send(JsonRpcNotification notification) {
        String json = JsonRpcCodec.encode(notification);
        writeFrame(json);
    }

    // --- Frame writing (synchronized) ---

    private void writeFrame(String json) {
        byte[] body = json.getBytes(StandardCharsets.UTF_8);
        String header = HEADER_PREFIX + body.length + "\r\n\r\n";
        synchronized (output) {
            try {
                output.write(header.getBytes(StandardCharsets.UTF_8));
                output.write(body);
                output.flush();
            } catch (IOException e) {
                if (handler != null) {
                    handler.onError(e);
                }
            }
        }
    }

    // --- Frame reading (background thread) ---

    private void readLoop() {
        while (running && !Thread.currentThread().isInterrupted()) {
            try {
                int contentLength = readContentLengthHeader();
                if (contentLength < 0) {
                    // EOF
                    break;
                }
                if (contentLength > maxMessageSize) {
                    handler.onError(new IOException(
                            "Message size " + contentLength + " exceeds maximum " + maxMessageSize));
                    // Skip the oversized body to resync
                    skipFully(input, contentLength);
                    continue;
                }
                byte[] body = readFully(input, contentLength);
                String json = new String(body, StandardCharsets.UTF_8);
                Object decoded = JsonRpcCodec.decodeRequest(json);
                if (decoded instanceof JsonRpcRequest req) {
                    handler.onRequest(req);
                } else if (decoded instanceof JsonRpcNotification notif) {
                    handler.onNotification(notif);
                }
            } catch (HeaderParseException e) {
                // Recoverable: malformed frame header, keep reading
                handler.onError(e);
            } catch (JsonRpcProtocolException e) {
                handler.onError(e);
            } catch (IOException e) {
                if (!running) {
                    break; // Expected during shutdown
                }
                handler.onError(e);
                break; // Stream errors are not recoverable
            } catch (Exception e) {
                handler.onError(e);
            }
        }
    }

    /**
     * Read the Content-Length header from the input stream.
     * Reads lines until an empty line (\r\n\r\n) is encountered.
     * Returns -1 on EOF.
     */
    private int readContentLengthHeader() throws IOException, HeaderParseException {
        // Look for the \r\n\r\n separator that ends the header section.
        // Track the last 4 bytes to detect the separator.
        byte[] tail = new byte[4];
        int tailLen = 0;
        ByteArrayOutputStream headerBuf = new ByteArrayOutputStream();
        int b;
        while ((b = input.read()) != -1) {
            headerBuf.write(b);
            // Shift tail window
            if (tailLen < 4) {
                tail[tailLen++] = (byte) b;
            } else {
                System.arraycopy(tail, 1, tail, 0, 3);
                tail[3] = (byte) b;
            }
            // Check for \r\n\r\n
            if (tailLen >= 4
                    && tail[tailLen - 4] == '\r' && tail[tailLen - 3] == '\n'
                    && tail[tailLen - 2] == '\r' && tail[tailLen - 1] == '\n') {
                String header = headerBuf.toString(StandardCharsets.UTF_8);
                return parseContentLength(header);
            }
        }
        return -1; // EOF
    }

    private int parseContentLength(String header) throws HeaderParseException {
        Matcher matcher = CONTENT_LENGTH_PATTERN.matcher(header);
        if (matcher.find()) {
            return Integer.parseInt(matcher.group(1));
        }
        throw new HeaderParseException("Missing Content-Length header in frame");
    }

    /**
     * Recoverable header parse error. The reader continues after this.
     */
    static class HeaderParseException extends Exception {
        HeaderParseException(String message) {
            super(message);
        }
    }

    private static byte[] readFully(InputStream in, int length) throws IOException {
        byte[] buf = new byte[length];
        int offset = 0;
        while (offset < length) {
            int read = in.read(buf, offset, length - offset);
            if (read == -1) {
                throw new IOException("Unexpected end of stream while reading frame body");
            }
            offset += read;
        }
        return buf;
    }

    private static void skipFully(InputStream in, long n) throws IOException {
        long remaining = n;
        while (remaining > 0) {
            long skipped = in.skip(remaining);
            if (skipped == 0) {
                // skip() returned 0, try reading a byte
                if (in.read() == -1) {
                    throw new IOException("Unexpected end of stream while skipping");
                }
                remaining--;
            } else {
                remaining -= skipped;
            }
        }
    }
}
