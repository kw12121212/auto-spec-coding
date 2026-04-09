package org.specdriven.agent.tool;

/**
 * Thread-safe bounded output buffer for collecting process output.
 * <p>
 * When the buffer exceeds its capacity, oldest content is discarded
 * to stay within the limit (tail-truncation — keeps the most recent output).
 */
class OutputRingBuffer {

    private final int maxBytes;
    private final StringBuilder sb = new StringBuilder();

    OutputRingBuffer(int maxBytes) {
        this.maxBytes = maxBytes;
    }

    /**
     * Appends text to the buffer.
     * Discards oldest content if total size would exceed maxBytes.
     */
    synchronized void append(String text) {
        if (text == null || text.isEmpty()) return;
        sb.append(text);
        truncateIfNeeded();
    }

    /**
     * Returns the current buffer contents.
     */
    synchronized String snapshot() {
        return sb.toString();
    }

    /**
     * Returns the current approximate byte size.
     */
    synchronized int size() {
        return sb.length() * 2; // UTF-16 chars, approximate
    }

    private void truncateIfNeeded() {
        // Use char count as proxy for byte count (conservative for ASCII/Latin-1)
        while (sb.length() > maxBytes) {
            // Remove from the beginning, keeping the tail
            int excess = sb.length() - maxBytes;
            sb.delete(0, Math.max(excess, 1024)); // Remove in chunks for efficiency
        }
    }
}
