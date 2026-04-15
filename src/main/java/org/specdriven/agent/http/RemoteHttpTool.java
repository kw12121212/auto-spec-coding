package org.specdriven.agent.http;

import org.specdriven.agent.tool.Tool;
import org.specdriven.agent.tool.ToolContext;
import org.specdriven.agent.tool.ToolInput;
import org.specdriven.agent.tool.ToolParameter;
import org.specdriven.agent.tool.ToolResult;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Tool implementation that delegates execution to a registered HTTP callback.
 */
public class RemoteHttpTool implements Tool {

    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(30);

    private final String name;
    private final String description;
    private final List<ToolParameter> parameters;
    private final URI callbackUri;
    private final HttpClient httpClient;

    public RemoteHttpTool(RemoteToolRegistrationRequest request) {
        this(request, HttpClient.newHttpClient());
    }

    RemoteHttpTool(RemoteToolRegistrationRequest request, HttpClient httpClient) {
        this.name = requireText(request.name(), "remote tool name");
        this.description = request.description() != null ? request.description() : "";
        this.parameters = toToolParameters(request.parameters());
        this.callbackUri = URI.create(requireText(request.callbackUrl(), "remote tool callback URL"));
        this.httpClient = httpClient;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public String getDescription() {
        return description;
    }

    @Override
    public List<ToolParameter> getParameters() {
        return parameters;
    }

    @Override
    public ToolResult execute(ToolInput input, ToolContext context) {
        RemoteToolInvocationRequest payload = new RemoteToolInvocationRequest(name, input.parameters());
        HttpRequest request = HttpRequest.newBuilder(callbackUri)
                .timeout(REQUEST_TIMEOUT)
                .header("Accept", "application/json")
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(HttpJsonCodec.encode(payload)))
                .build();
        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                return new ToolResult.Error("remote tool callback returned HTTP " + response.statusCode());
            }
            RemoteToolInvocationResponse decoded = HttpJsonCodec.decodeRemoteToolInvocationResponse(response.body());
            if (decoded.success()) {
                return new ToolResult.Success(decoded.output() != null ? decoded.output() : "");
            }
            return new ToolResult.Error(decoded.error() != null ? decoded.error() : "remote tool callback failed");
        } catch (IOException e) {
            return new ToolResult.Error("remote tool callback transport failed: " + e.getMessage(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return new ToolResult.Error("remote tool callback interrupted", e);
        } catch (RuntimeException e) {
            return new ToolResult.Error("remote tool callback response was invalid: " + e.getMessage(), e);
        }
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value.trim();
    }

    private static List<ToolParameter> toToolParameters(List<Map<String, Object>> rawParameters) {
        List<ToolParameter> converted = new ArrayList<>();
        for (Map<String, Object> raw : rawParameters) {
            String name = asString(raw.get("name"));
            String type = asString(raw.get("type"));
            String description = asString(raw.get("description"));
            boolean required = raw.get("required") instanceof Boolean b && b;
            converted.add(new ToolParameter(name, type, description, required));
        }
        return List.copyOf(converted);
    }

    private static String asString(Object value) {
        return value != null ? String.valueOf(value) : null;
    }
}
