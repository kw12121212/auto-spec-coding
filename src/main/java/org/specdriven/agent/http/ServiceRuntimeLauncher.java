package org.specdriven.agent.http;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.specdriven.agent.json.JsonWriter;
import org.specdriven.sdk.LealonePlatform;
import org.specdriven.sdk.PlatformConfig;
import org.specdriven.sdk.SpecDriven;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Starts the minimal service-application runtime backed by the existing SDK/platform
 * bootstrap and application-service HTTP invocation contract.
 */
public final class ServiceRuntimeLauncher {

    private static final String DEFAULT_HOST = "127.0.0.1";
    private static final int DEFAULT_PORT = 8080;
    private static final int MIN_PORT = 0;
    private static final int MAX_PORT = 65535;

    public RuntimeHandle start(Options options) {
        return start(options, null);
    }

    RuntimeHandle start(Options options, ServiceInvoker serviceInvoker) {
        Objects.requireNonNull(options, "options must not be null");
        options.validate();

        SpecDriven sdk = null;
        try {
            PlatformConfig platformConfig = new PlatformConfig(
                    options.jdbcUrl(),
                    options.compileCachePath());
            sdk = SpecDriven.builder()
                    .platformConfig(platformConfig)
                    .build();

            LealonePlatform.ServiceApplicationBootstrapResult bootstrap =
                    sdk.bootstrapServices(options.servicesSqlPath());
            sdk.platform().start();

            ServiceRuntimeHttpEndpoint endpoint = ServiceRuntimeHttpEndpoint.start(
                    options.host(),
                    options.port(),
                    serviceInvoker != null
                            ? serviceInvoker
                            : new LealoneServiceInvoker(sdk.platform().database().jdbcUrl()),
                    options.apiKeys());
            StartupResult startup = new StartupResult(
                    "started",
                    options.servicesSqlPath().toAbsolutePath().normalize(),
                    endpoint.host(),
                    endpoint.port(),
                    "http://" + endpoint.host() + ":" + endpoint.port() + "/services",
                    "http://" + endpoint.host() + ":" + endpoint.port() + "/api/v1/health",
                    bootstrap.appliedStatements());
            return new RuntimeHandle(sdk, endpoint, startup);
        } catch (RuntimeException e) {
            closeQuietly(sdk);
            if (e instanceof ServiceRuntimeException runtimeException) {
                throw runtimeException;
            }
            String errorCode = classifyStartupError(e);
            throw new ServiceRuntimeException(errorCode, e.getMessage(), e);
        }
    }

    private String classifyStartupError(RuntimeException e) {
        if (e instanceof LealonePlatform.BootstrapValidationException) {
            return "bootstrap_error";
        }
        if (e instanceof IllegalArgumentException) {
            String message = e.getMessage() != null ? e.getMessage() : "";
            if (message.contains("services.sql")) {
                return "missing_input";
            }
            return "invalid_config";
        }
        return "bootstrap_error";
    }

    private static void closeQuietly(SpecDriven sdk) {
        if (sdk != null) {
            try {
                sdk.close();
            } catch (Exception ignored) {
            }
        }
    }

    public static Options defaultOptions(Path servicesSqlPath) {
        PlatformConfig defaults = PlatformConfig.defaults();
        return new Options(
                servicesSqlPath,
                DEFAULT_HOST,
                DEFAULT_PORT,
                defaults.jdbcUrl(),
                defaults.compileCachePath(),
                Set.of());
    }

    public record Options(
            Path servicesSqlPath,
            String host,
            int port,
            String jdbcUrl,
            Path compileCachePath,
            Set<String> apiKeys) {

        public Options {
            Objects.requireNonNull(servicesSqlPath, "servicesSqlPath must not be null");
            host = host == null || host.isBlank() ? DEFAULT_HOST : host.trim();
            jdbcUrl = jdbcUrl == null || jdbcUrl.isBlank() ? PlatformConfig.defaults().jdbcUrl() : jdbcUrl.trim();
            compileCachePath = compileCachePath != null ? compileCachePath : PlatformConfig.defaults().compileCachePath();
            apiKeys = apiKeys != null ? Set.copyOf(apiKeys) : Set.of();
        }

        void validate() {
            if (port < MIN_PORT || port > MAX_PORT) {
                throw new ServiceRuntimeException(
                        "invalid_config",
                        "port must be between " + MIN_PORT + " and " + MAX_PORT);
            }
            Path normalizedServicesSql = servicesSqlPath.toAbsolutePath().normalize();
            if (!Files.isRegularFile(normalizedServicesSql) || !Files.isReadable(normalizedServicesSql)) {
                throw new ServiceRuntimeException(
                        "missing_input",
                        "services.sql must reference a readable file: " + normalizedServicesSql);
            }
            if (jdbcUrl.isBlank()) {
                throw new ServiceRuntimeException("invalid_config", "jdbcUrl must not be blank");
            }
        }
    }

    public record StartupResult(
            String status,
            Path servicesSql,
            String host,
            int port,
            String serviceBaseUrl,
            String healthUrl,
            int appliedStatements) {

        public String toJson() {
            Map<String, Object> value = new LinkedHashMap<>();
            value.put("status", status);
            value.put("servicesSql", servicesSql.toString());
            value.put("host", host);
            value.put("port", port);
            value.put("serviceBaseUrl", serviceBaseUrl);
            value.put("healthUrl", healthUrl);
            value.put("appliedStatements", appliedStatements);
            return JsonWriter.fromMap(value);
        }
    }

    public static String failureJson(String error, String message) {
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("status", "failed");
        value.put("error", error);
        value.put("message", message);
        return JsonWriter.fromMap(value);
    }

    public static final class RuntimeHandle implements AutoCloseable {
        private final SpecDriven sdk;
        private final ServiceRuntimeHttpEndpoint endpoint;
        private final StartupResult startupResult;

        private RuntimeHandle(SpecDriven sdk, ServiceRuntimeHttpEndpoint endpoint, StartupResult startupResult) {
            this.sdk = Objects.requireNonNull(sdk, "sdk must not be null");
            this.endpoint = Objects.requireNonNull(endpoint, "endpoint must not be null");
            this.startupResult = Objects.requireNonNull(startupResult, "startupResult must not be null");
        }

        public StartupResult startupResult() {
            return startupResult;
        }

        public void await() throws InterruptedException {
            Thread.currentThread().join();
        }

        @Override
        public void close() {
            endpoint.close();
            closeQuietly(sdk);
        }
    }

    public static final class ServiceRuntimeException extends RuntimeException {
        private final String errorCode;

        public ServiceRuntimeException(String errorCode, String message) {
            super(message);
            this.errorCode = Objects.requireNonNull(errorCode, "errorCode must not be null");
        }

        public ServiceRuntimeException(String errorCode, String message, Throwable cause) {
            super(message, cause);
            this.errorCode = Objects.requireNonNull(errorCode, "errorCode must not be null");
        }

        public String errorCode() {
            return errorCode;
        }
    }

    private static final class ServiceRuntimeHttpEndpoint implements AutoCloseable {
        private final HttpServer server;
        private final String host;
        private final ExecutorService executor;

        private ServiceRuntimeHttpEndpoint(HttpServer server, String host, ExecutorService executor) {
            this.server = server;
            this.host = host;
            this.executor = executor;
        }

        static ServiceRuntimeHttpEndpoint start(
                String host,
                int port,
                ServiceInvoker serviceInvoker,
                Set<String> apiKeys) {
            try {
                HttpServer server = HttpServer.create(new InetSocketAddress(host, port), 0);
                ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
                server.setExecutor(executor);
                server.createContext("/", exchange -> handle(exchange, serviceInvoker, apiKeys));
                server.start();
                return new ServiceRuntimeHttpEndpoint(server, host, executor);
            } catch (IOException | RuntimeException e) {
                throw new ServiceRuntimeException(
                        "http_startup_error",
                        e.getMessage() != null ? e.getMessage() : "Failed to start service HTTP endpoint",
                        e);
            }
        }

        String host() {
            return host;
        }

        int port() {
            return server.getAddress().getPort();
        }

        @Override
        public void close() {
            server.stop(0);
            executor.shutdownNow();
        }

        private static void handle(HttpExchange exchange, ServiceInvoker serviceInvoker, Set<String> apiKeys) {
            try {
                String path = exchange.getRequestURI().getPath();
                String method = exchange.getRequestMethod();
                if ("/api/v1/health".equals(path)) {
                    requireMethod(method, "GET", "/api/v1/health");
                    sendJson(exchange, 200, HttpJsonCodec.encode(new HealthResponse("ok", "0.1.0")));
                    return;
                }
                if (path.startsWith("/services/")) {
                    requireApiKey(exchange, apiKeys);
                    handleService(exchange, serviceInvoker, path, method);
                    return;
                }
                throw new HttpApiException(404, "not_found", "Unknown route");
            } catch (HttpApiException e) {
                sendJson(exchange, e.httpStatus(), HttpJsonCodec.encode(e.toErrorResponse()));
            } catch (Exception e) {
                sendJson(exchange, 500, HttpJsonCodec.encode(new ErrorResponse(
                        500,
                        "internal",
                        e.getMessage() != null ? e.getMessage() : "Internal error",
                        null)));
            } finally {
                exchange.close();
            }
        }

        private static void handleService(
                HttpExchange exchange,
                ServiceInvoker serviceInvoker,
                String path,
                String method) throws IOException {
            requireMethod(method, "POST", path);
            String[] segments = path.split("/");
            if (segments.length != 4 || segments[2].isBlank() || segments[3].isBlank()) {
                throw new HttpApiException(404, "not_found", "Unknown service route");
            }
            String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            if (body.isBlank()) {
                throw new HttpApiException(400, "invalid_params", "Request body required");
            }
            sendJson(exchange, 200, ServiceHttpInvocationHandler.invoke(serviceInvoker, segments[2], segments[3], body));
        }

        private static void requireMethod(String actual, String expected, String path) {
            if (!expected.equals(actual)) {
                throw new HttpApiException(405, "method_not_allowed", expected + " required for " + path);
            }
        }

        private static void requireApiKey(HttpExchange exchange, Set<String> apiKeys) {
            if (apiKeys == null || apiKeys.isEmpty()) {
                return;
            }
            String key = extractApiKey(exchange);
            if (key == null || !apiKeys.contains(key)) {
                throw new HttpApiException(401, "unauthorized", "Authentication required");
            }
        }

        private static String extractApiKey(HttpExchange exchange) {
            List<String> authHeaders = exchange.getRequestHeaders().get("Authorization");
            if (authHeaders != null) {
                for (String auth : authHeaders) {
                    if (auth != null && auth.startsWith("Bearer ")) {
                        String token = auth.substring(7).trim();
                        if (!token.isEmpty()) {
                            return token;
                        }
                    }
                }
            }
            List<String> apiKeys = exchange.getRequestHeaders().get("X-API-Key");
            if (apiKeys != null) {
                for (String apiKey : apiKeys) {
                    if (apiKey != null && !apiKey.isBlank()) {
                        return apiKey.trim();
                    }
                }
            }
            return null;
        }

        private static void sendJson(HttpExchange exchange, int status, String json) {
            byte[] bytes = json != null ? json.getBytes(StandardCharsets.UTF_8) : new byte[0];
            try {
                exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
                exchange.sendResponseHeaders(status, bytes.length);
                try (OutputStream body = exchange.getResponseBody()) {
                    body.write(bytes);
                }
            } catch (IOException ignored) {
            }
        }
    }
}
