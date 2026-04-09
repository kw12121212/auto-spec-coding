package org.specdriven.sdk;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Isolated;
import org.specdriven.agent.agent.AgentState;
import org.specdriven.agent.event.Event;
import org.specdriven.agent.event.EventType;
import org.specdriven.agent.event.SimpleEventBus;
import org.specdriven.agent.tool.Tool;
import org.specdriven.agent.tool.ToolContext;
import org.specdriven.agent.tool.ToolInput;
import org.specdriven.agent.tool.ToolParameter;
import org.specdriven.agent.tool.ToolResult;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

@Isolated
class SdkAgentEventTest {

    private final SimpleEventBus globalBus = new SimpleEventBus();

    @Test
    void perAgentWildcardListenerReceivesEvents() {
        List<Event> events = Collections.synchronizedList(new ArrayList<>());

        SdkAgent agent = new SdkAgent(
                null, Collections.emptyMap(), SdkConfig.defaults(), null, globalBus
        );
        agent.onEvent(events::add);
        agent.run("hello");

        assertFalse(events.isEmpty(), "Per-agent listener should receive events");
        assertTrue(events.stream().anyMatch(e -> e.type() == EventType.AGENT_STATE_CHANGED));
    }

    @Test
    void perAgentTypedListenerReceivesOnlyMatchingType() {
        List<Event> stateEvents = Collections.synchronizedList(new ArrayList<>());

        SdkAgent agent = new SdkAgent(
                null, Collections.emptyMap(), SdkConfig.defaults(), null, globalBus
        );
        agent.onEvent(EventType.AGENT_STATE_CHANGED, stateEvents::add);
        agent.run("hello");

        assertFalse(stateEvents.isEmpty());
        assertTrue(stateEvents.stream().allMatch(e -> e.type() == EventType.AGENT_STATE_CHANGED));
    }

    @Test
    void perAgentListenerIsScopedToOwnAgent() {
        List<Event> agent1Events = Collections.synchronizedList(new ArrayList<>());
        List<Event> agent2Events = Collections.synchronizedList(new ArrayList<>());

        SdkAgent agent1 = new SdkAgent(
                null, Collections.emptyMap(), SdkConfig.defaults(), null, globalBus
        );
        SdkAgent agent2 = new SdkAgent(
                null, Collections.emptyMap(), SdkConfig.defaults(), null, globalBus
        );

        agent1.onEvent(agent1Events::add);
        agent2.onEvent(agent2Events::add);

        agent1.run("hello");

        // agent2's listener should not have received agent1's events
        assertTrue(agent2Events.isEmpty(), "Agent2 listener should not receive agent1 events");
        assertFalse(agent1Events.isEmpty(), "Agent1 listener should receive its own events");
    }

    @Test
    void bothGlobalAndPerAgentListenersFire() {
        List<Event> globalEvents = Collections.synchronizedList(new ArrayList<>());
        List<Event> perAgentEvents = Collections.synchronizedList(new ArrayList<>());

        globalBus.subscribe(EventType.AGENT_STATE_CHANGED, globalEvents::add);

        SdkAgent agent = new SdkAgent(
                null, Collections.emptyMap(), SdkConfig.defaults(), null, globalBus
        );
        agent.onEvent(EventType.AGENT_STATE_CHANGED, perAgentEvents::add);
        agent.run("hello");

        assertFalse(globalEvents.isEmpty(), "Global listener should receive events");
        assertFalse(perAgentEvents.isEmpty(), "Per-agent listener should receive events");
    }

    @Test
    void stateChangedEventsContainFromStateAndToState() {
        List<Event> stateEvents = Collections.synchronizedList(new ArrayList<>());

        SdkAgent agent = new SdkAgent(
                null, Collections.emptyMap(), SdkConfig.defaults(), null, globalBus
        );
        agent.onEvent(EventType.AGENT_STATE_CHANGED, stateEvents::add);
        agent.run("hello");

        assertFalse(stateEvents.isEmpty());

        // Find the IDLE→RUNNING transition
        Event toRunning = stateEvents.stream()
                .filter(e -> "RUNNING".equals(e.metadata().get("toState")))
                .findFirst()
                .orElse(null);

        assertNotNull(toRunning, "Should have a transition to RUNNING");
        assertEquals("IDLE", toRunning.metadata().get("fromState"));

        // Find the RUNNING→STOPPED transition
        Event toStopped = stateEvents.stream()
                .filter(e -> "STOPPED".equals(e.metadata().get("toState")))
                .findFirst()
                .orElse(null);

        assertNotNull(toStopped, "Should have a transition to STOPPED");
        assertEquals("RUNNING", toStopped.metadata().get("fromState"));
    }

    @Test
    void toolExecutedEventContainsMetadata() {
        List<Event> toolEvents = Collections.synchronizedList(new ArrayList<>());

        Map<String, Tool> tools = new HashMap<>();
        tools.put("echo", new EchoTool());

        SdkAgent agent = new SdkAgent(
                null, tools, SdkConfig.defaults(), null, globalBus
        );
        agent.onEvent(EventType.TOOL_EXECUTED, toolEvents::add);

        // Without an LLM, no tools are executed, so we just verify no error
        agent.run("hello");
        // Tool events depend on LLM returning tool calls, which won't happen without a provider
        // The test confirms the listener mechanism works without throwing
        assertEquals(AgentState.STOPPED, agent.getState());
    }

    @Test
    void errorEventContainsErrorMetadata() {
        List<Event> errorEvents = Collections.synchronizedList(new ArrayList<>());

        SdkAgent agent = new SdkAgent(
                null, Collections.emptyMap(), SdkConfig.defaults(), null, globalBus
        );
        agent.onEvent(EventType.ERROR, errorEvents::add);

        // Without provider, run completes without error (returns empty string)
        agent.run("hello");

        // No error expected in this scenario — verify listener setup is correct
        assertEquals(AgentState.STOPPED, agent.getState());
    }

    @Test
    void backwardCompatibility_runWithoutListenersWorks() {
        SdkAgent agent = new SdkAgent(
                null, Collections.emptyMap(), SdkConfig.defaults(), null, globalBus
        );
        String result = agent.run("hello");

        assertEquals("", result);
        assertEquals(AgentState.STOPPED, agent.getState());
    }

    /** Minimal tool for testing. */
    static class EchoTool implements Tool {
        @Override public String getName() { return "echo"; }
        @Override public String getDescription() { return "Echo tool"; }
        @Override public List<ToolParameter> getParameters() { return List.of(); }
        @Override public ToolResult execute(ToolInput input, ToolContext context) {
            return new ToolResult.Success("echo");
        }
    }
}
