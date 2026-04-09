package org.specdriven.agent.tool;

import java.time.Duration;

/**
 * Declarative descriptor for a server readiness probe configuration.
 *
 * @param type          probe type (TCP or HTTP), must not be null
 * @param host          target hostname, defaults to "localhost"
 * @param port          target port number
 * @param path          URL path for HTTP probes, nullable (defaults to "/")
 * @param expectedStatus expected HTTP response status code, defaults to 200
 * @param timeout       maximum total time to wait for readiness, defaults to 30s
 * @param retryInterval time between probe attempts, defaults to 1s
 * @param maxRetries    maximum number of probe attempts, defaults to 30
 */
public record ReadyProbe(
        ProbeType type,
        String host,
        int port,
        String path,
        int expectedStatus,
        Duration timeout,
        Duration retryInterval,
        int maxRetries
) {
    public ReadyProbe {
        if (type == null) throw new NullPointerException("type must not be null");
        if (host == null || host.isBlank()) host = "localhost";
        if (path == null) path = "/";
        if (timeout == null) timeout = Duration.ofSeconds(30);
        if (retryInterval == null) retryInterval = Duration.ofSeconds(1);
        if (expectedStatus <= 0) expectedStatus = 200;
        if (maxRetries <= 0) maxRetries = 30;
    }

    /**
     * Creates a TCP probe with defaults for all optional fields.
     */
    public static ReadyProbe tcp(int port) {
        return new ReadyProbe(ProbeType.TCP, "localhost", port, null, 200,
                Duration.ofSeconds(30), Duration.ofSeconds(1), 30);
    }

    /**
     * Creates an HTTP probe with defaults for all optional fields.
     */
    public static ReadyProbe http(int port) {
        return new ReadyProbe(ProbeType.HTTP, "localhost", port, "/", 200,
                Duration.ofSeconds(30), Duration.ofSeconds(1), 30);
    }

    /**
     * Creates an HTTP probe with a custom path.
     */
    public static ReadyProbe http(int port, String path) {
        return new ReadyProbe(ProbeType.HTTP, "localhost", port, path, 200,
                Duration.ofSeconds(30), Duration.ofSeconds(1), 30);
    }
}
