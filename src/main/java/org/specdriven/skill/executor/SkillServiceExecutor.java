package org.specdriven.skill.executor;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;

import com.lealone.db.service.Service;
import com.lealone.db.service.ServiceExecutor;
import org.specdriven.agent.agent.AgentState;
import org.specdriven.agent.agent.ClaudeProviderFactory;
import org.specdriven.agent.agent.Conversation;
import org.specdriven.agent.agent.DefaultLlmProviderRegistry;
import org.specdriven.agent.agent.DefaultOrchestrator;
import org.specdriven.agent.agent.LlmClient;
import org.specdriven.agent.agent.LlmProviderFactory;
import org.specdriven.agent.agent.Orchestrator;
import org.specdriven.agent.agent.OrchestratorConfig;
import org.specdriven.agent.agent.OpenAiProviderFactory;
import org.specdriven.agent.agent.SimpleAgentContext;
import org.specdriven.agent.agent.SystemMessage;
import org.specdriven.agent.agent.UserMessage;
import org.specdriven.agent.config.Config;
import org.specdriven.agent.config.ConfigLoader;
import org.specdriven.agent.hook.PermissionCheckHook;
import org.specdriven.agent.tool.BashTool;
import org.specdriven.agent.tool.EditTool;
import org.specdriven.agent.tool.GlobTool;
import org.specdriven.agent.tool.GrepTool;
import org.specdriven.agent.tool.ReadTool;
import org.specdriven.agent.tool.Tool;
import org.specdriven.agent.tool.WriteTool;
import org.specdriven.skill.sql.SkillSqlException;
import org.specdriven.skill.store.FileSystemInstructionStore;
import org.specdriven.skill.store.SkillInstructionStore;

public class SkillServiceExecutor implements ServiceExecutor {

    private static final Map<String, Tool> DEFAULT_TOOLS = Map.of(
            "bash", new BashTool(),
            "read", new ReadTool(),
            "write", new WriteTool(),
            "edit", new EditTool(),
            "glob", new GlobTool(),
            "grep", new GrepTool()
    );

    private final Service service;
    private final SkillParameterParser parameterParser;
    private final SkillInstructionStore instructionStore;
    private final Function<Path, LlmClient> llmClientFactory;
    private final Map<String, Tool> toolRegistry;

    private volatile SkillParameterParser.SkillParameters parameters;

    public SkillServiceExecutor(Service service) {
        this(service, new SkillParameterParser(), new FileSystemInstructionStore(),
                DefaultSkillLlmClientFactory::create, DEFAULT_TOOLS);
    }

    SkillServiceExecutor(Service service,
                         SkillParameterParser parameterParser,
                         SkillInstructionStore instructionStore,
                         Function<Path, LlmClient> llmClientFactory,
                         Map<String, Tool> toolRegistry) {
        this.service = Objects.requireNonNull(service, "service must not be null");
        this.parameterParser = Objects.requireNonNull(parameterParser, "parameterParser must not be null");
        this.instructionStore = Objects.requireNonNull(instructionStore, "instructionStore must not be null");
        this.llmClientFactory = Objects.requireNonNull(llmClientFactory, "llmClientFactory must not be null");
        this.toolRegistry = Collections.unmodifiableMap(new HashMap<>(Objects.requireNonNull(toolRegistry, "toolRegistry must not be null")));
    }

    @Override
    public Object executeService(String methodName, Map<String, Object> methodArgs) {
        if (!"EXECUTE".equalsIgnoreCase(methodName)) {
            throw noMethodException(methodName);
        }

        SkillParameterParser.SkillParameters resolvedParameters = parameters();
        String skillId = resolvedParameters.skillId();
        Path skillDir = resolvedParameters.skillDir();

        String instructions = instructionStore.loadInstructions(skillId, skillDir);
        String prompt = methodArgs != null ? toString("PROMPT", methodArgs) : null;

        Conversation conversation = new Conversation();
        conversation.append(new SystemMessage(instructions, System.currentTimeMillis()));
        conversation.append(new UserMessage(prompt != null ? prompt : "", System.currentTimeMillis()));

        Map<String, String> contextConfig = new HashMap<>();
        contextConfig.put("workDir", skillDir.toAbsolutePath().toString());
        contextConfig.put("skill_id", skillId);

        SimpleAgentContext context = new SimpleAgentContext(
                UUID.randomUUID().toString(),
                contextConfig,
                toolRegistry,
                conversation
        );

        OrchestratorConfig baseConfig = OrchestratorConfig.defaults();
        OrchestratorConfig orchestratorConfig = new OrchestratorConfig(
                baseConfig.maxTurns(),
                baseConfig.toolTimeoutSeconds(),
                List.of(new PermissionCheckHook())
        );
        Orchestrator orchestrator = new DefaultOrchestrator(orchestratorConfig, () -> AgentState.RUNNING);
        RecordingLlmClient llmClient = new RecordingLlmClient(llmClientFactory.apply(skillDir));
        orchestrator.run(context, llmClient);

        if (llmClient.lastResponse() instanceof org.specdriven.agent.agent.LlmResponse.TextResponse textResponse) {
            return textResponse.content();
        }
        return "";
    }

    private SkillParameterParser.SkillParameters parameters() {
        SkillParameterParser.SkillParameters local = parameters;
        if (local != null) {
            return local;
        }
        synchronized (this) {
            if (parameters == null) {
                parameters = parameterParser.parse(service.getCreateSQL());
            }
            return parameters;
        }
    }

    private static final class DefaultSkillLlmClientFactory {
        private static volatile DefaultLlmProviderRegistry registry;

        private DefaultSkillLlmClientFactory() {}

        static LlmClient create(Path skillDir) {
            return registry(skillDir).defaultProvider().createClient();
        }

        private static DefaultLlmProviderRegistry registry(Path skillDir) {
            DefaultLlmProviderRegistry local = registry;
            if (local != null) {
                return local;
            }
            synchronized (DefaultSkillLlmClientFactory.class) {
                if (registry == null) {
                    registry = loadRegistry(skillDir);
                }
                return registry;
            }
        }

        private static DefaultLlmProviderRegistry loadRegistry(Path skillDir) {
            Path configPath = resolveConfigPath(skillDir);
            if (configPath != null) {
                Config config = ConfigLoader.load(configPath, true);
                return DefaultLlmProviderRegistry.fromConfig(config, factories());
            }

            Map<String, String> envConfig = resolveEnvConfig();
            if (envConfig != null) {
                DefaultLlmProviderRegistry registry = new DefaultLlmProviderRegistry();
                String providerName = envConfig.get("providerName");
                String providerType = envConfig.remove("providerType");
                envConfig.remove("providerName");
                LlmProviderFactory factory = factories().get(providerType);
                if (factory == null) {
                    throw new IllegalStateException("Unsupported LLM provider type from environment: " + providerType);
                }
                registry.register(providerName, factory.create(org.specdriven.agent.agent.LlmConfig.fromMap(envConfig)));
                registry.setDefault(providerName);
                return registry;
            }

            throw new IllegalStateException(
                    "No LLM provider configuration found. Set SPEC_DRIVEN_AGENT_CONFIG or provide OPENAI_API_KEY / ANTHROPIC_API_KEY.");
        }

        private static Path resolveConfigPath(Path skillDir) {
            String explicit = System.getenv("SPEC_DRIVEN_AGENT_CONFIG");
            if (explicit != null && !explicit.isBlank()) {
                return Path.of(explicit);
            }

            String property = System.getProperty("specdriven.agent.config");
            if (property != null && !property.isBlank()) {
                return Path.of(property);
            }

            Path cwdConfig = Path.of(System.getProperty("user.dir", "."), "agent.yaml");
            return Files.isRegularFile(cwdConfig) ? cwdConfig : null;
        }

        private static Map<String, String> resolveEnvConfig() {
            String openAiKey = System.getenv("OPENAI_API_KEY");
            if (openAiKey != null && !openAiKey.isBlank()) {
                Map<String, String> config = new HashMap<>();
                config.put("providerType", "openai");
                config.put("providerName", "openai-env");
                config.put("apiKey", openAiKey);
                String baseUrl = System.getenv("OPENAI_BASE_URL");
                if (baseUrl != null && !baseUrl.isBlank()) {
                    config.put("baseUrl", baseUrl);
                }
                String model = System.getenv("OPENAI_MODEL");
                if (model != null && !model.isBlank()) {
                    config.put("model", model);
                }
                return config;
            }

            String anthropicKey = System.getenv("ANTHROPIC_API_KEY");
            if (anthropicKey != null && !anthropicKey.isBlank()) {
                Map<String, String> config = new HashMap<>();
                config.put("providerType", "claude");
                config.put("providerName", "claude-env");
                config.put("apiKey", anthropicKey);
                String baseUrl = System.getenv("ANTHROPIC_BASE_URL");
                if (baseUrl != null && !baseUrl.isBlank()) {
                    config.put("baseUrl", baseUrl);
                } else {
                    config.put("baseUrl", "https://api.anthropic.com/v1");
                }
                String model = System.getenv("ANTHROPIC_MODEL");
                if (model != null && !model.isBlank()) {
                    config.put("model", model);
                } else {
                    throw new IllegalStateException("ANTHROPIC_MODEL must be set when using ANTHROPIC_API_KEY");
                }
                return config;
            }

            return null;
        }

        private static Map<String, LlmProviderFactory> factories() {
            return Map.of(
                    "openai", new OpenAiProviderFactory(),
                    "claude", new ClaudeProviderFactory()
            );
        }
    }

    private static final class RecordingLlmClient implements LlmClient {
        private final LlmClient delegate;
        private volatile org.specdriven.agent.agent.LlmResponse lastResponse;

        private RecordingLlmClient(LlmClient delegate) {
            this.delegate = delegate;
        }

        @Override
        public org.specdriven.agent.agent.LlmResponse chat(List<org.specdriven.agent.agent.Message> messages) {
            org.specdriven.agent.agent.LlmResponse response = delegate.chat(messages);
            lastResponse = response;
            return response;
        }

        private org.specdriven.agent.agent.LlmResponse lastResponse() {
            return lastResponse;
        }
    }
}
