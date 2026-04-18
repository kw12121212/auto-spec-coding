package org.specdriven.agent.http;

import jakarta.servlet.FilterChain;
import jakarta.servlet.FilterConfig;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.LongSupplier;

/**
 * Per-client fixed-window rate limit filter. Clients are identified by their API key
 * (set by {@link AuthFilter}). When the configured request threshold is exceeded within
 * the window, the filter returns 429 with a {@code Retry-After} header.
 */
public class RateLimitFilter extends HttpFilter {

    private int maxRequests;
    private long windowMillis;
    private final ConcurrentHashMap<String, RateLimitWindow> windows = new ConcurrentHashMap<>();
    private LongSupplier clock;

    public RateLimitFilter() {
        this(100, 60);
    }

    /** Constructor for programmatic configuration. */
    public RateLimitFilter(int maxRequests, int windowSeconds) {
        this(maxRequests, windowSeconds, System::currentTimeMillis);
    }

    /** Constructor for programmatic configuration with injectable clock (for testing). */
    RateLimitFilter(int maxRequests, int windowSeconds, LongSupplier clock) {
        this.maxRequests = maxRequests;
        this.windowMillis = windowSeconds * 1000L;
        this.clock = clock;
    }

    @Override
    public void init(FilterConfig config) {
        String maxParam = config.getInitParameter("RATE_LIMIT_MAX");
        String windowParam = config.getInitParameter("RATE_LIMIT_WINDOW_SECONDS");
        if (maxParam != null) {
            maxRequests = Integer.parseInt(maxParam);
        }
        if (windowParam != null) {
            windowMillis = Long.parseLong(windowParam) * 1000L;
        }
        if (clock == null) {
            clock = System::currentTimeMillis;
        }
    }

    @Override
    protected void doFilter(HttpServletRequest req, HttpServletResponse resp, FilterChain chain)
            throws IOException, ServletException {
        if (isCallbackRequest(req)) {
            chain.doFilter(req, resp);
            return;
        }

        String clientId = resolveClientId(req);
        RateLimitWindow window = windows.compute(clientId, (k, w) -> {
            long now = clock.getAsLong();
            if (w == null || now - w.startMillis >= windowMillis) {
                return new RateLimitWindow(now, 1);
            }
            return new RateLimitWindow(w.startMillis, w.count + 1);
        });

        if (window.count > maxRequests) {
            long retryAfter = (window.startMillis + windowMillis - clock.getAsLong()) / 1000;
            if (retryAfter < 1) retryAfter = 1;
            sendRateLimited(resp, (int) retryAfter);
            return;
        }

        chain.doFilter(req, resp);
    }

    private boolean isCallbackRequest(HttpServletRequest req) {
        String uri = req.getRequestURI();
        return uri != null && uri.contains("/callbacks/");
    }

    private String resolveClientId(HttpServletRequest req) {
        String auth = req.getHeader("Authorization");
        if (auth != null && auth.startsWith("Bearer ")) {
            String token = auth.substring(7).trim();
            if (!token.isEmpty()) return token;
        }
        String apiKey = req.getHeader("X-API-Key");
        if (apiKey != null && !apiKey.isBlank()) {
            return apiKey.trim();
        }
        return req.getRemoteAddr();
    }

    private void sendRateLimited(HttpServletResponse resp, int retryAfter) throws IOException {
        ErrorResponse error = new ErrorResponse(429, "rate_limited", "Rate limit exceeded", null);
        String json = HttpJsonCodec.encode(error);
        resp.setStatus(429);
        resp.setContentType("application/json; charset=utf-8");
        resp.setHeader("Retry-After", String.valueOf(retryAfter));
        resp.getWriter().write(json);
    }

    // Visible for testing
    record RateLimitWindow(long startMillis, long count) {}
}
