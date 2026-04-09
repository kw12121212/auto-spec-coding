package org.specdriven.agent.tool;

import java.net.HttpURLConnection;
import java.net.URI;

/**
 * HTTP GET probe strategy.
 * <p>
 * Sends an HTTP GET to the target URL and checks if the response status code
 * matches the probe's expected status. Returns true on match, false otherwise.
 */
public class HttpProbeStrategy implements ProbeStrategy {

    @Override
    public boolean probe(ReadyProbe probe) {
        try {
            String path = probe.path() != null ? probe.path() : "/";
            String url = "http://" + probe.host() + ":" + probe.port() + path;
            HttpURLConnection conn = (HttpURLConnection) URI.create(url).toURL().openConnection();
            int timeoutMs = (int) Math.min(probe.retryInterval().toMillis(), Integer.MAX_VALUE);
            conn.setConnectTimeout(timeoutMs);
            conn.setReadTimeout(timeoutMs);
            conn.setRequestMethod("GET");
            int status = conn.getResponseCode();
            // Consume response body to allow connection reuse
            try (var is = status < 400 ? conn.getInputStream() : conn.getErrorStream()) {
                if (is != null) is.skip(Integer.MAX_VALUE);
            } catch (Exception ignored) {}
            conn.disconnect();
            return status == probe.expectedStatus();
        } catch (Exception e) {
            return false;
        }
    }
}
