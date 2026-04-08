package org.specdriven.agent.http;

import jakarta.servlet.*;
import jakarta.servlet.http.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.io.*;
import java.security.Principal;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class RateLimitFilterTest {

    private static final String API_KEY = "client-key-1";
    private static final int MAX_REQUESTS = 3;
    private static final int WINDOW_SECONDS = 60;

    private RateLimitFilter filter;
    private StubFilterChain chain;

    @BeforeEach
    void setUp() {
        filter = new RateLimitFilter(MAX_REQUESTS, WINDOW_SECONDS);
        chain = new StubFilterChain();
    }

    @Nested
    class UnderLimit {

        @Test
        void requestsUnderLimit_pass() throws Exception {
            for (int i = 0; i < MAX_REQUESTS; i++) {
                chain = new StubFilterChain();
                StubRequest req = requestWithKey(API_KEY);
                StubResponse resp = new StubResponse();
                filter.doFilter(req, resp, chain);
                assertTrue(chain.wasCalled(), "Request " + (i + 1) + " should pass");
                assertEquals(200, resp.status());
            }
        }
    }

    @Nested
    class OverLimit {

        @Test
        void requestOverLimit_returns429() throws Exception {
            // Exhaust the limit
            for (int i = 0; i < MAX_REQUESTS; i++) {
                chain = new StubFilterChain();
                filter.doFilter(requestWithKey(API_KEY), new StubResponse(), chain);
            }

            // Next request should be rejected
            chain = new StubFilterChain();
            StubResponse resp = new StubResponse();
            filter.doFilter(requestWithKey(API_KEY), resp, chain);
            assertFalse(chain.wasCalled());
            assertEquals(429, resp.status());
            assertTrue(resp.body().contains("\"error\":\"rate_limited\""));
        }

        @Test
        void rateLimitedResponse_hasRetryAfterHeader() throws Exception {
            for (int i = 0; i < MAX_REQUESTS; i++) {
                chain = new StubFilterChain();
                filter.doFilter(requestWithKey(API_KEY), new StubResponse(), chain);
            }

            StubResponse resp = new StubResponse();
            filter.doFilter(requestWithKey(API_KEY), resp, chain);
            assertNotNull(resp.retryAfter, "Retry-After header should be set");
            assertTrue(Integer.parseInt(resp.retryAfter) >= 1, "Retry-After should be >= 1 second");
            assertTrue(Integer.parseInt(resp.retryAfter) >= 1);
        }
    }

    @Nested
    class WindowReset {

        @Test
        void differentClient_hasIndependentLimit() throws Exception {
            // Exhaust limit for client 1
            for (int i = 0; i < MAX_REQUESTS; i++) {
                chain = new StubFilterChain();
                filter.doFilter(requestWithKey("client-1"), new StubResponse(), chain);
            }

            // Client 2 should still be allowed
            chain = new StubFilterChain();
            StubResponse resp = new StubResponse();
            filter.doFilter(requestWithKey("client-2"), resp, chain);
            assertTrue(chain.wasCalled(), "Different client should have independent limit");
            assertEquals(200, resp.status());
        }
    }

    @Nested
    class ErrorResponseFormat {

        @Test
        void rateLimitedResponse_isJson() throws Exception {
            for (int i = 0; i < MAX_REQUESTS; i++) {
                chain = new StubFilterChain();
                filter.doFilter(requestWithKey(API_KEY), new StubResponse(), chain);
            }

            StubResponse resp = new StubResponse();
            filter.doFilter(requestWithKey(API_KEY), resp, chain);
            assertTrue(resp.contentType().contains("application/json"));
            assertTrue(resp.body().contains("\"status\":429"));
            assertTrue(resp.body().contains("\"error\":\"rate_limited\""));
        }
    }

    // --- Helpers ---

    private StubRequest requestWithKey(String key) {
        return new StubRequest("GET", "/agent/run", Map.of("Authorization", "Bearer " + key));
    }

    // --- Stub types ---

    static class StubRequest extends HttpServletRequestWrapper {
        private final String method;
        private final String pathInfo;
        private final Map<String, String> headers;

        StubRequest(String method, String pathInfo, Map<String, String> headers) {
            super(new NullRequest());
            this.method = method;
            this.pathInfo = pathInfo;
            this.headers = headers;
        }

        @Override public String getMethod() { return method; }
        @Override public String getPathInfo() { return pathInfo; }
        @Override public String getHeader(String name) { return headers.get(name); }
        @Override public String getRequestURI() { return "/api/v1" + pathInfo; }
        @Override public String getRemoteAddr() { return "127.0.0.1"; }
    }

    static class StubResponse extends HttpServletResponseWrapper {
        private int status = 200;
        private String contentType;
        private String retryAfter;
        private final StringWriter writer = new StringWriter();

        StubResponse() { super(new NullResponse()); }

        @Override public void setStatus(int sc) { this.status = sc; }
        @Override public void setContentType(String type) { this.contentType = type; }
        @Override public void setHeader(String name, String value) {
            if ("Retry-After".equalsIgnoreCase(name)) retryAfter = value;
        }
        @Override public PrintWriter getWriter() { return new PrintWriter(writer, true); }

        int status() { return status; }
        String body() { return writer.toString(); }
        String contentType() { return contentType != null ? contentType : ""; }
    }

    static class StubFilterChain implements FilterChain {
        private boolean called;
        @Override public void doFilter(ServletRequest req, ServletResponse resp) { called = true; }
        boolean wasCalled() { return called; }
    }

    private static class NullRequest implements HttpServletRequest {
        public Object getAttribute(String n) { return null; }
        public Enumeration<String> getAttributeNames() { return Collections.emptyEnumeration(); }
        public void setAttribute(String n, Object o) {}
        public void removeAttribute(String n) {}
        public String getCharacterEncoding() { return "UTF-8"; }
        public void setCharacterEncoding(String e) {}
        public int getContentLength() { return -1; }
        public long getContentLengthLong() { return -1; }
        public String getContentType() { return null; }
        public ServletInputStream getInputStream() { return null; }
        public String getParameter(String n) { return null; }
        public Enumeration<String> getParameterNames() { return Collections.emptyEnumeration(); }
        public String[] getParameterValues(String n) { return null; }
        public Map<String, String[]> getParameterMap() { return Map.of(); }
        public String getProtocol() { return "HTTP/1.1"; }
        public String getScheme() { return "http"; }
        public String getServerName() { return "localhost"; }
        public int getServerPort() { return 8080; }
        public BufferedReader getReader() { return new BufferedReader(new StringReader("")); }
        public String getRemoteAddr() { return "127.0.0.1"; }
        public String getRemoteHost() { return "localhost"; }
        public Locale getLocale() { return Locale.ENGLISH; }
        public Enumeration<Locale> getLocales() { return Collections.enumeration(List.of(Locale.ENGLISH)); }
        public boolean isSecure() { return false; }
        public RequestDispatcher getRequestDispatcher(String p) { return null; }
        public int getRemotePort() { return 0; }
        public String getLocalName() { return "localhost"; }
        public String getLocalAddr() { return "127.0.0.1"; }
        public int getLocalPort() { return 8080; }
        public ServletContext getServletContext() { return null; }
        public AsyncContext startAsync() { throw new IllegalStateException(); }
        public AsyncContext startAsync(ServletRequest r, ServletResponse s) { throw new IllegalStateException(); }
        public boolean isAsyncStarted() { return false; }
        public boolean isAsyncSupported() { return false; }
        public AsyncContext getAsyncContext() { return null; }
        public DispatcherType getDispatcherType() { return DispatcherType.REQUEST; }
        public String getRequestId() { return ""; }
        public String getProtocolRequestId() { return ""; }
        public ServletConnection getServletConnection() { return null; }
        public String getAuthType() { return null; }
        public jakarta.servlet.http.Cookie[] getCookies() { return null; }
        public long getDateHeader(String n) { return -1; }
        public String getHeader(String n) { return null; }
        public Enumeration<String> getHeaders(String n) { return Collections.emptyEnumeration(); }
        public Enumeration<String> getHeaderNames() { return Collections.emptyEnumeration(); }
        public int getIntHeader(String n) { return -1; }
        public String getMethod() { return "GET"; }
        public String getPathInfo() { return null; }
        public String getPathTranslated() { return null; }
        public String getContextPath() { return "/api/v1"; }
        public String getQueryString() { return null; }
        public String getRemoteUser() { return null; }
        public boolean isUserInRole(String r) { return false; }
        public Principal getUserPrincipal() { return null; }
        public String getRequestedSessionId() { return null; }
        public StringBuffer getRequestURL() { return new StringBuffer("http://localhost:8080/api/v1"); }
        public String getRequestURI() { return "/api/v1"; }
        public String getServletPath() { return ""; }
        public HttpSession getSession(boolean c) { return null; }
        public HttpSession getSession() { return null; }
        public String changeSessionId() { return null; }
        public boolean isRequestedSessionIdValid() { return false; }
        public boolean isRequestedSessionIdFromCookie() { return false; }
        public boolean isRequestedSessionIdFromURL() { return false; }
        public boolean authenticate(HttpServletResponse r) { return false; }
        public void login(String u, String p) {}
        public void logout() {}
        public Collection<Part> getParts() { return List.of(); }
        public Part getPart(String n) { return null; }
        public <T extends HttpUpgradeHandler> T upgrade(Class<T> c) { return null; }
    }

    private static class NullResponse implements HttpServletResponse {
        public String getCharacterEncoding() { return "UTF-8"; }
        public String getContentType() { return null; }
        public void setCharacterEncoding(String c) {}
        public void setContentLength(int l) {}
        public void setContentLengthLong(long l) {}
        public void setContentType(String t) {}
        public void setBufferSize(int s) {}
        public int getBufferSize() { return 0; }
        public void flushBuffer() {}
        public void resetBuffer() {}
        public boolean isCommitted() { return false; }
        public void reset() {}
        public void setLocale(Locale l) {}
        public Locale getLocale() { return Locale.ENGLISH; }
        public void addCookie(jakarta.servlet.http.Cookie c) {}
        public boolean containsHeader(String n) { return false; }
        public String encodeURL(String u) { return u; }
        public String encodeRedirectURL(String u) { return u; }
        public String encodeUrl(String u) { return u; }
        public String encodeRedirectUrl(String u) { return u; }
        public void sendError(int s, String m) {}
        public void sendError(int s) {}
        public void sendRedirect(String l) {}
        public void sendRedirect(String l, int s, boolean c) {}
        public void setDateHeader(String n, long d) {}
        public void addDateHeader(String n, long d) {}
        public void setHeader(String n, String v) {}
        public void addHeader(String n, String v) {}
        public void setIntHeader(String n, int v) {}
        public void addIntHeader(String n, int v) {}
        public void setStatus(int s) {}
        public void setStatus(int s, String m) {}
        public int getStatus() { return 200; }
        public String getHeader(String n) { return null; }
        public Collection<String> getHeaders(String n) { return List.of(); }
        public Collection<String> getHeaderNames() { return List.of(); }
        public PrintWriter getWriter() { return new PrintWriter(new StringWriter()); }
        public ServletOutputStream getOutputStream() { return null; }
    }
}
