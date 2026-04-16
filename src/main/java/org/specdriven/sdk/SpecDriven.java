package org.specdriven.sdk;

import org.specdriven.agent.agent.LlmProviderRegistry;
import org.specdriven.agent.event.EventBus;
import org.specdriven.agent.question.*;
import org.specdriven.agent.tool.Tool;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Main entry point for the SpecDriven SDK.
 * Create instances via {@link #builder()}.
 */
public class SpecDriven implements AutoCloseable {

    private final LealonePlatform platform;
    private final LlmProviderRegistry providerRegistry;
    private final List<Tool> tools;
    private final Map<String, Tool> remoteTools = new ConcurrentHashMap<>();
    private final String systemPrompt;
    private final SdkConfig sdkConfig;
    private final Map<String, String> configMap;
    private final EventBus eventBus;
    private final DeliveryMode deliveryModeOverride;
    private final MobileChannelRegistry channelRegistry;
    private final List<MobileChannelConfig> channelConfigs;
    private final WorkflowRuntime workflowRuntime;
    private volatile QuestionDeliveryService deliveryService;

    SpecDriven(LealonePlatform platform,
               LlmProviderRegistry providerRegistry,
               List<Tool> tools,
               String systemPrompt,
               SdkConfig sdkConfig,
               Map<String, String> configMap,
               EventBus eventBus,
               DeliveryMode deliveryModeOverride,
               MobileChannelRegistry channelRegistry,
               List<MobileChannelConfig> channelConfigs) {
        this.platform = platform;
        this.providerRegistry = providerRegistry;
        this.tools = tools;
        this.systemPrompt = systemPrompt;
        this.sdkConfig = sdkConfig;
        this.configMap = configMap;
        this.eventBus = eventBus;
        this.deliveryModeOverride = deliveryModeOverride;
        this.channelRegistry = channelRegistry;
        this.channelConfigs = channelConfigs;
        this.workflowRuntime = new WorkflowRuntime(eventBus, List.of());
    }

    /**
     * Returns a new builder for constructing SDK instances.
     */
    public static SdkBuilder builder() {
        return new SdkBuilder();
    }

    /**
     * Returns the assembled platform capabilities backing this SDK instance.
     */
    public LealonePlatform platform() {
        return platform;
    }

    /**
     * Applies a supported {@code services.sql} bootstrap entry through the assembled platform.
     */
    public LealonePlatform.ServiceApplicationBootstrapResult bootstrapServices(Path servicesSqlPath) {
        return platform.bootstrapServices(servicesSqlPath);
    }

    /**
     * Creates a new agent handle configured with this SDK's tools and providers.
     */
    public SdkAgent createAgent() {
        Map<String, Tool> toolMap = new HashMap<>();
        for (Tool tool : allTools()) {
            toolMap.put(tool.getName(), tool);
        }
        return new SdkAgent(providerRegistry, toolMap, sdkConfig, systemPrompt,
                eventBus, deliveryModeOverride, deliveryService());
    }

    /**
     * Registers a workflow through the supported domain declaration path.
     */
    public void declareWorkflow(String workflowName) {
        workflowRuntime.declareWorkflow(workflowName);
    }

    /**
     * Registers a workflow with an ordered step list through the supported domain declaration path.
     */
    public void declareWorkflow(String workflowName, List<WorkflowStep> steps) {
        workflowRuntime.declareWorkflow(workflowName, steps);
    }

    /**
     * Registers a workflow through the supported SQL declaration path.
     */
    public void declareWorkflowSql(String sql) {
        workflowRuntime.declareWorkflowSql(sql);
    }

    /**
     * Registers a workflow with an ordered step list through the supported SQL declaration path.
     */
    public void declareWorkflowSql(String sql, List<WorkflowStep> steps) {
        workflowRuntime.declareWorkflowSql(sql, steps);
    }

    /**
     * Starts a previously declared workflow by name.
     */
    public WorkflowInstanceView startWorkflow(String workflowName, Map<String, Object> input) {
        return workflowRuntime.startWorkflow(workflowName, input);
    }

    /**
     * Returns the current state view for a workflow instance.
     */
    public WorkflowInstanceView workflowState(String workflowId) {
        return workflowRuntime.workflowState(workflowId);
    }

    /**
     * Returns the current result view for a workflow instance.
     */
    public WorkflowResultView workflowResult(String workflowId) {
        return workflowRuntime.workflowResult(workflowId);
    }

    /**
     * Returns the list of tools registered with this SDK instance.
     */
    public List<Tool> tools() {
        return allTools();
    }

    /**
     * Returns whether a tool name belongs to the static SDK tool set supplied by the builder.
     */
    public boolean hasStaticTool(String name) {
        if (name == null) {
            return false;
        }
        return tools.stream().anyMatch(tool -> name.equals(tool.getName()));
    }

    /**
     * Registers or replaces a callback-backed remote tool.
     */
    public void registerRemoteTool(Tool tool) {
        if (tool == null || tool.getName() == null || tool.getName().isBlank()) {
            throw new IllegalArgumentException("remote tool name must not be blank");
        }
        if (hasStaticTool(tool.getName())) {
            throw new IllegalArgumentException("remote tool cannot replace static tool: " + tool.getName());
        }
        remoteTools.put(tool.getName(), tool);
    }

    private List<Tool> allTools() {
        List<Tool> combined = new ArrayList<>(tools);
        combined.addAll(remoteTools.values());
        return List.copyOf(combined);
    }

    /**
     * Returns the shared event bus for this SDK instance.
     */
    public EventBus eventBus() {
        return eventBus;
    }

    /**
     * Returns the question delivery service, initializing lazily on first access.
     * Returns null if initialization fails (e.g., Lealone not available).
     */
    public QuestionDeliveryService deliveryService() {
        if (deliveryService == null) {
            synchronized (this) {
                if (deliveryService == null) {
                    try {
                        QuestionRuntime questionRuntime = new QuestionRuntime(eventBus);
                        QuestionStore questionStore = new LealoneQuestionStore(eventBus,
                                platform.database().jdbcUrl());
                        questionRuntime.setQuestionStore(questionStore);

                        QuestionDeliveryChannel channel;
                        QuestionReplyCollector collector;

                        if (channelConfigs != null && !channelConfigs.isEmpty()) {
                            List<MobileChannelHandle> handles = channelRegistry.assembleAll(channelConfigs);
                            MobileChannelHandle first = handles.get(0);
                            channel = first.channel();
                            collector = first.collector();
                        } else {
                            channel = new LoggingDeliveryChannel();
                            collector = new InMemoryReplyCollector(questionRuntime);
                        }

                        deliveryService = new QuestionDeliveryService(
                                channel, collector, questionRuntime, questionStore);
                    } catch (Exception e) {
                        // Delivery service is optional — agent runs fine without it
                        return null;
                    }
                }
            }
        }
        return deliveryService;
    }

    /**
     * Returns the global delivery mode override, or null if not set.
     */
    DeliveryMode deliveryModeOverride() {
        return deliveryModeOverride;
    }

    /**
     * Closes all resources held by this SDK instance, including the provider registry.
     */
    @Override
    public void close() {
        QuestionDeliveryService ds = deliveryService;
        if (ds != null) {
            try { ds.channel().close(); } catch (Exception ignored) {}
            try { ds.collector().close(); } catch (Exception ignored) {}
        }
        try { workflowRuntime.close(); } catch (Exception ignored) {}
        if (platform != null) {
            try { platform.close(); } catch (Exception ignored) {}
        }
    }
}
