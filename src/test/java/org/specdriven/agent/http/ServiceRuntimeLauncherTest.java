package org.specdriven.agent.http;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.specdriven.agent.json.JsonReader;
import org.specdriven.sdk.PlatformConfig;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ServiceRuntimeLauncherTest {

    @TempDir
    Path tempDir;

    @Test
    void exposesServiceNamespaceAndPreservesHealthRoute() throws Exception {
        Path servicesSql = tempDir.resolve("services.sql");
        Files.writeString(servicesSql, "\n", StandardCharsets.UTF_8);
        ServiceRuntimeLauncher.Options options = options(servicesSql, Set.of("test-key"));
        ServiceInvoker invoker = (serviceName, methodName, args) -> {
            assertEquals("invoice", serviceName);
            assertEquals("create", methodName);
            assertEquals(List.of(1L, "x"), args);
            return Map.of("id", "inv-1", "status", "created");
        };

        try (ServiceRuntimeLauncher.RuntimeHandle handle = new ServiceRuntimeLauncher().start(options, invoker)) {
            ServiceRuntimeLauncher.StartupResult startup = handle.startupResult();
            HttpClient client = HttpClient.newHttpClient();

            HttpResponse<String> health = client.send(HttpRequest.newBuilder()
                    .uri(URI.create(startup.healthUrl()))
                    .GET()
                    .build(), HttpResponse.BodyHandlers.ofString());
            assertEquals(200, health.statusCode());
            assertEquals("ok", JsonReader.getString(JsonReader.parseObject(health.body()), "status"));

            HttpResponse<String> service = client.send(HttpRequest.newBuilder()
                    .uri(URI.create(startup.serviceBaseUrl() + "/invoice/create"))
                    .header("Authorization", "Bearer test-key")
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString("{\"args\":[1,\"x\"]}"))
                    .build(), HttpResponse.BodyHandlers.ofString());
            assertEquals(200, service.statusCode());
            assertTrue(service.body().contains("\"id\":\"inv-1\""));

            HttpResponse<String> apiRoute = client.send(HttpRequest.newBuilder()
                    .uri(URI.create("http://" + startup.host() + ":" + startup.port() + "/api/v1/not-a-service"))
                    .GET()
                    .build(), HttpResponse.BodyHandlers.ofString());
            assertEquals(404, apiRoute.statusCode());
            assertEquals("not_found", JsonReader.getString(JsonReader.parseObject(apiRoute.body()), "error"));
        }
    }

    @Test
    void serviceNamespaceRequiresConfiguredApiKey() throws Exception {
        Path servicesSql = tempDir.resolve("services.sql");
        Files.writeString(servicesSql, "\n", StandardCharsets.UTF_8);

        try (ServiceRuntimeLauncher.RuntimeHandle handle = new ServiceRuntimeLauncher().start(
                options(servicesSql, Set.of("test-key")),
                (serviceName, methodName, args) -> Map.of())) {
            ServiceRuntimeLauncher.StartupResult startup = handle.startupResult();

            HttpResponse<String> response = HttpClient.newHttpClient().send(HttpRequest.newBuilder()
                    .uri(URI.create(startup.serviceBaseUrl() + "/invoice/create"))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString("{\"args\":[]}"))
                    .build(), HttpResponse.BodyHandlers.ofString());

            assertEquals(401, response.statusCode());
            assertEquals("unauthorized", JsonReader.getString(JsonReader.parseObject(response.body()), "error"));
        }
    }

    @Test
    void defaultOptionsUsePlatformDefaults() {
        Path servicesSql = tempDir.resolve("services.sql");

        ServiceRuntimeLauncher.Options options = ServiceRuntimeLauncher.defaultOptions(servicesSql);

        assertEquals("127.0.0.1", options.host());
        assertEquals(8080, options.port());
        assertEquals(PlatformConfig.defaults().jdbcUrl(), options.jdbcUrl());
        assertEquals(PlatformConfig.defaults().compileCachePath(), options.compileCachePath());
        assertEquals(Set.of(), options.apiKeys());
    }

    @Test
    void startupRejectsRuntimeDirectivesFromServicesSql() throws Exception {
        Path servicesSql = tempDir.resolve("services.sql");
        Files.writeString(servicesSql, "SET MODE MYSQL;\n", StandardCharsets.UTF_8);

        ServiceRuntimeLauncher.ServiceRuntimeException error = org.junit.jupiter.api.Assertions.assertThrows(
                ServiceRuntimeLauncher.ServiceRuntimeException.class,
                () -> new ServiceRuntimeLauncher().start(options(servicesSql, Set.of()), (serviceName, methodName, args) -> Map.of()));

        assertEquals("bootstrap_error", error.errorCode());
        assertTrue(error.getMessage().contains("Unsupported services.sql statement"));
    }

    private ServiceRuntimeLauncher.Options options(Path servicesSql, Set<String> apiKeys) {
        PlatformConfig defaults = PlatformConfig.defaults();
        return new ServiceRuntimeLauncher.Options(
                servicesSql,
                "127.0.0.1",
                0,
                "jdbc:lealone:embed:runtime_" + Long.toHexString(System.nanoTime()),
                defaults.compileCachePath(),
                apiKeys);
    }
}
