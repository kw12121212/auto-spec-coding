package org.specdriven.agent.http;

import jakarta.servlet.DispatcherType;
import jakarta.servlet.Filter;
import org.apache.catalina.Context;
import org.apache.catalina.startup.Tomcat;
import org.apache.tomcat.util.descriptor.web.FilterDef;
import org.apache.tomcat.util.descriptor.web.FilterMap;
import org.specdriven.sdk.SpecDriven;

import java.io.File;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.LongSupplier;

/**
 * Embedded HTTP stack fixture for exercising the REST API through Tomcat,
 * servlet filters, and {@link HttpApiServlet}.
 */
final class HttpTestStack implements AutoCloseable {

    private final String apiKey;
    private final int rateLimitMax;
    private final int rateLimitWindowSeconds;
    private final SpecDriven sdk;
    private final HttpClient client;
    private final LongSupplier rateLimitClock;
    private Tomcat tomcat;
    private int port;

    HttpTestStack(String apiKey, int rateLimitMax, int rateLimitWindowSeconds, SpecDriven sdk) {
        this(apiKey, rateLimitMax, rateLimitWindowSeconds, sdk, System::currentTimeMillis);
    }

    HttpTestStack(String apiKey, int rateLimitMax, int rateLimitWindowSeconds, SpecDriven sdk,
                  LongSupplier rateLimitClock) {
        this.apiKey = Objects.requireNonNull(apiKey, "apiKey");
        this.rateLimitMax = rateLimitMax;
        this.rateLimitWindowSeconds = rateLimitWindowSeconds;
        this.sdk = Objects.requireNonNull(sdk, "sdk");
        this.rateLimitClock = Objects.requireNonNull(rateLimitClock, "rateLimitClock");
        this.client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();
    }

    HttpTestStack start() throws Exception {
        tomcat = new Tomcat();
        tomcat.setBaseDir(System.getProperty("java.io.tmpdir") + "/tomcat-e2e-" + UUID.randomUUID());
        tomcat.setPort(0);

        Context ctx = tomcat.addContext("", new File(".").getCanonicalPath());
        addFilter(ctx, "authFilter", new AuthFilter(), "/api/v1/*", Map.of("API_KEYS", apiKey));
        addFilter(ctx, "rateLimitFilter",
                new RateLimitFilter(rateLimitMax, rateLimitWindowSeconds, rateLimitClock),
                "/api/v1/*", Map.of(
                        "RATE_LIMIT_MAX", String.valueOf(rateLimitMax),
                        "RATE_LIMIT_WINDOW_SECONDS", String.valueOf(rateLimitWindowSeconds)));

        Tomcat.addServlet(ctx, "apiServlet", new HttpApiServlet(sdk));
        ctx.addServletMappingDecoded("/api/v1/*", "apiServlet");

        tomcat.start();
        port = tomcat.getConnector().getLocalPort();
        return this;
    }

    String baseUrl() {
        ensureStarted();
        return "http://localhost:" + port + "/api/v1";
    }

    int port() {
        ensureStarted();
        return port;
    }

    HttpResponse<String> get(String path) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl() + path))
                .GET()
                .build();
        return client.send(request, HttpResponse.BodyHandlers.ofString());
    }

    HttpResponse<String> getWithBearer(String path) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl() + path))
                .header("Authorization", "Bearer " + apiKey)
                .GET()
                .build();
        return client.send(request, HttpResponse.BodyHandlers.ofString());
    }

    HttpResponse<String> getWithApiKey(String path) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl() + path))
                .header("X-API-Key", apiKey)
                .GET()
                .build();
        return client.send(request, HttpResponse.BodyHandlers.ofString());
    }

    HttpResponse<String> getWithBearerToken(String path, String bearerToken) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl() + path))
                .header("Authorization", "Bearer " + bearerToken)
                .GET()
                .build();
        return client.send(request, HttpResponse.BodyHandlers.ofString());
    }

    HttpResponse<String> postWithBearer(String path, String body) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl() + path))
                .header("Authorization", "Bearer " + apiKey)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
        return client.send(request, HttpResponse.BodyHandlers.ofString());
    }

    HttpResponse<String> postWithBearerNoBody(String path) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl() + path))
                .header("Authorization", "Bearer " + apiKey)
                .POST(HttpRequest.BodyPublishers.ofString(""))
                .build();
        return client.send(request, HttpResponse.BodyHandlers.ofString());
    }

    @Override
    public void close() throws Exception {
        Exception failure = null;
        if (tomcat != null) {
            try {
                tomcat.stop();
            } catch (Exception e) {
                failure = e;
            }
            try {
                tomcat.destroy();
            } catch (Exception e) {
                if (failure == null) {
                    failure = e;
                } else {
                    failure.addSuppressed(e);
                }
            }
            tomcat = null;
        }
        try {
            sdk.close();
        } catch (Exception e) {
            if (failure == null) {
                failure = e;
            } else {
                failure.addSuppressed(e);
            }
        }
        if (failure != null) {
            throw failure;
        }
    }

    private static void addFilter(Context ctx, String name, Filter filter, String urlPattern,
                                  Map<String, String> initParams) {
        FilterDef def = new FilterDef();
        def.setFilterName(name);
        def.setFilter(filter);
        def.setFilterClass(filter.getClass().getName());
        initParams.forEach(def::addInitParameter);
        ctx.addFilterDef(def);

        FilterMap map = new FilterMap();
        map.setFilterName(name);
        map.addURLPatternDecoded(urlPattern);
        map.setDispatcher(DispatcherType.REQUEST.name());
        ctx.addFilterMap(map);
    }

    private void ensureStarted() {
        if (tomcat == null || port <= 0) {
            throw new IllegalStateException("HTTP test stack has not been started");
        }
    }
}
