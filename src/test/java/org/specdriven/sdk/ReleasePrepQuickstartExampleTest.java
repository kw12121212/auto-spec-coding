package org.specdriven.sdk;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Isolated;
import org.specdriven.agent.agent.DefaultLlmProviderRegistry;
import org.specdriven.agent.agent.LlmClient;
import org.specdriven.agent.agent.LlmConfig;
import org.specdriven.agent.agent.LlmProvider;
import org.specdriven.agent.agent.LlmResponse;
import org.specdriven.agent.agent.Message;
import org.specdriven.agent.tool.Tool;
import org.specdriven.agent.tool.ToolContext;
import org.specdriven.agent.tool.ToolInput;
import org.specdriven.agent.tool.ToolParameter;
import org.specdriven.agent.tool.ToolResult;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

@Isolated
class ReleasePrepQuickstartExampleTest {

    @Test
    void javaSdkQuickstartFlowRunsAgainstConfiguredProvider() {
        DefaultLlmProviderRegistry registry = new DefaultLlmProviderRegistry();
        registry.register("stub", new StubProvider("Hello from the quickstart."));
        registry.setDefault("stub");

        try (SpecDriven sdk = SpecDriven.builder()
                .providerRegistry(registry)
                .registerTool(new ExampleTool())
                .systemPrompt("You are a concise coding assistant.")
                .build()) {
            String output = sdk.createAgent().run("Explain this repository in one paragraph.");

            assertEquals("Hello from the quickstart.", output);
            assertEquals(List.of("example-echo"), sdk.tools().stream().map(Tool::getName).toList());
        }
    }

    private static final class StubProvider implements LlmProvider {
        private final String response;

        private StubProvider(String response) {
            this.response = response;
        }

        @Override
        public LlmConfig config() {
            return new LlmConfig("https://example.invalid/v1", "test-key", "stub-model", 30, 0);
        }

        @Override
        public LlmClient createClient() {
            return new LlmClient() {
                @Override
                public LlmResponse chat(List<Message> messages) {
                    return new LlmResponse.TextResponse(response);
                }
            };
        }

        @Override
        public void close() {
        }
    }

    private static final class ExampleTool implements Tool {

        @Override
        public String getName() {
            return "example-echo";
        }

        @Override
        public String getDescription() {
            return "Returns the provided text unchanged.";
        }

        @Override
        public List<ToolParameter> getParameters() {
            return List.of(new ToolParameter("text", "string", "text to echo", true));
        }

        @Override
        public ToolResult execute(ToolInput input, ToolContext context) {
            return new ToolResult.Success(String.valueOf(input.parameters().getOrDefault("text", "")));
        }
    }
}
