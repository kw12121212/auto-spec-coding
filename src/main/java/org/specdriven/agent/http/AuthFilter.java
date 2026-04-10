package org.specdriven.agent.http;

import jakarta.servlet.FilterChain;
import jakarta.servlet.FilterConfig;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.util.Set;

/**
 * Authentication filter that validates API key from {@code Authorization: Bearer <key>}
 * or {@code X-API-Key} header. The {@code /health} endpoint bypasses auth.
 */
public class AuthFilter extends HttpFilter {

    private Set<String> validKeys;

    @Override
    public void init(FilterConfig config) {
        String keysParam = config.getInitParameter("API_KEYS");
        if (keysParam == null || keysParam.isBlank()) {
            keysParam = System.getenv("API_KEYS");
        }
        if (keysParam != null && !keysParam.isBlank()) {
            validKeys = Set.of(keysParam.split(","));
        } else {
            validKeys = Set.of();
        }
    }

    /** Constructor for programmatic configuration (testing / SDK builder). */
    public AuthFilter(Set<String> validKeys) {
        this.validKeys = validKeys != null ? Set.copyOf(validKeys) : Set.of();
    }

    public AuthFilter() {}

    @Override
    protected void doFilter(HttpServletRequest req, HttpServletResponse resp, FilterChain chain)
            throws IOException, ServletException {
        if (isHealthRequest(req) || isCallbackRequest(req)) {
            chain.doFilter(req, resp);
            return;
        }

        String key = extractApiKey(req);
        if (key == null || !validKeys.contains(key)) {
            sendUnauthorized(resp);
            return;
        }

        chain.doFilter(req, resp);
    }

    private boolean isHealthRequest(HttpServletRequest req) {
        String uri = req.getRequestURI();
        return uri != null && uri.endsWith("/health");
    }

    private boolean isCallbackRequest(HttpServletRequest req) {
        String uri = req.getRequestURI();
        return uri != null && uri.contains("/callbacks/");
    }

    private String extractApiKey(HttpServletRequest req) {
        String auth = req.getHeader("Authorization");
        if (auth != null && auth.startsWith("Bearer ")) {
            String token = auth.substring(7).trim();
            if (!token.isEmpty()) return token;
        }
        String apiKey = req.getHeader("X-API-Key");
        if (apiKey != null && !apiKey.isBlank()) {
            return apiKey.trim();
        }
        return null;
    }

    private void sendUnauthorized(HttpServletResponse resp) throws IOException {
        ErrorResponse error = new ErrorResponse(401, "unauthorized", "Authentication required", null);
        String json = HttpJsonCodec.encode(error);
        resp.setStatus(401);
        resp.setContentType("application/json; charset=utf-8");
        resp.getWriter().write(json);
    }
}
