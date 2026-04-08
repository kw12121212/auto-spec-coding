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

class AuthFilterTest {

    private static final String VALID_KEY = "test-key-123";
    private AuthFilter filter;
    private StubFilterChain chain;

    @BeforeEach
    void setUp() {
        filter = new AuthFilter(Set.of(VALID_KEY));
        chain = new StubFilterChain();
    }

    @Nested
    class BearerToken {

        @Test
        void validBearerToken_passes() throws Exception {
            StubRequest req = new StubRequest("GET", "/agent/run", Map.of("Authorization", "Bearer " + VALID_KEY));
            StubResponse resp = new StubResponse();
            filter.doFilter(req, resp, chain);
            assertTrue(chain.wasCalled());
            assertEquals(200, resp.status());
        }

        @Test
        void invalidBearerToken_returns401() throws Exception {
            StubRequest req = new StubRequest("GET", "/agent/run", Map.of("Authorization", "Bearer wrong-key"));
            StubResponse resp = new StubResponse();
            filter.doFilter(req, resp, chain);
            assertFalse(chain.wasCalled());
            assertEquals(401, resp.status());
            assertTrue(resp.body().contains("\"error\":\"unauthorized\""));
        }
    }

    @Nested
    class XApiKeyHeader {

        @Test
        void validXApiKey_passes() throws Exception {
            StubRequest req = new StubRequest("GET", "/tools", Map.of("X-API-Key", VALID_KEY));
            StubResponse resp = new StubResponse();
            filter.doFilter(req, resp, chain);
            assertTrue(chain.wasCalled());
        }

        @Test
        void invalidXApiKey_returns401() throws Exception {
            StubRequest req = new StubRequest("GET", "/tools", Map.of("X-API-Key", "bad-key"));
            StubResponse resp = new StubResponse();
            filter.doFilter(req, resp, chain);
            assertFalse(chain.wasCalled());
            assertEquals(401, resp.status());
        }
    }

    @Nested
    class MissingAuth {

        @Test
        void noAuthHeader_returns401() throws Exception {
            StubRequest req = new StubRequest("GET", "/agent/run", Map.of());
            StubResponse resp = new StubResponse();
            filter.doFilter(req, resp, chain);
            assertFalse(chain.wasCalled());
            assertEquals(401, resp.status());
            assertTrue(resp.body().contains("\"status\":401"));
        }
    }

    @Nested
    class HealthBypass {

        @Test
        void healthEndpoint_bypassesAuth() throws Exception {
            StubRequest req = new StubRequest("GET", "/api/v1/health", Map.of());
            req.requestUri = "/api/v1/health";
            StubResponse resp = new StubResponse();
            filter.doFilter(req, resp, chain);
            assertTrue(chain.wasCalled());
        }
    }

    @Nested
    class ErrorResponseFormat {

        @Test
        void errorResponse_isJsonWithCorrectFields() throws Exception {
            StubRequest req = new StubRequest("GET", "/agent/run", Map.of());
            StubResponse resp = new StubResponse();
            filter.doFilter(req, resp, chain);
            assertTrue(resp.contentType().contains("application/json"));
            assertTrue(resp.body().contains("\"status\":401"));
            assertTrue(resp.body().contains("\"error\":\"unauthorized\""));
            assertTrue(resp.body().contains("\"message\":\"Authentication required\""));
        }
    }

    // --- Stub types ---

    static class StubRequest extends HttpServletRequestWrapper {
        private final String method;
        private final String pathInfo;
        private final Map<String, String> headers;
        String requestUri;

        StubRequest(String method, String pathInfo, Map<String, String> headers) {
            super(new NullRequest());
            this.method = method;
            this.pathInfo = pathInfo;
            this.headers = headers;
            this.requestUri = "/api/v1" + pathInfo;
        }

        @Override public String getMethod() { return method; }
        @Override public String getPathInfo() { return pathInfo; }
        @Override public String getHeader(String name) { return headers.get(name); }
        @Override public String getRequestURI() { return requestUri; }
        @Override public String getRemoteAddr() { return "127.0.0.1"; }
    }

    static class StubResponse extends HttpServletResponseWrapper {
        private int status = 200;
        private String contentType;
        private final StringWriter writer = new StringWriter();

        StubResponse() { super(new NullResponse()); }

        @Override public void setStatus(int sc) { this.status = sc; }
        @Override public void setContentType(String type) { this.contentType = type; }
        @Override public void setHeader(String name, String value) {}
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
