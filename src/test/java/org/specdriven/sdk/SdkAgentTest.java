package org.specdriven.sdk;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Isolated;
import org.specdriven.agent.agent.*;
import org.specdriven.agent.event.SimpleEventBus;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@Isolated
class SdkAgentTest {

    private final SimpleEventBus eventBus = new SimpleEventBus();

    @Test
    void runWithNoProviderReturnsEmpty() {
        SdkAgent agent = new SdkAgent(
                null, Collections.emptyMap(), SdkConfig.defaults(), null, eventBus
        );
        String result = agent.run("hello");
        assertEquals("", result);
    }

    @Test
    void getStateBeforeRunIsNull() {
        SdkAgent agent = new SdkAgent(
                null, Collections.emptyMap(), SdkConfig.defaults(), null, eventBus
        );
        assertNull(agent.getState());
    }

    @Test
    void getStateAfterRunIsStopped() {
        SdkAgent agent = new SdkAgent(
                null, Collections.emptyMap(), SdkConfig.defaults(), null, eventBus
        );
        agent.run("hello");
        assertEquals(AgentState.STOPPED, agent.getState());
    }

    @Test
    void stopOnFreshAgentDoesNotThrow() {
        SdkAgent agent = new SdkAgent(
                null, Collections.emptyMap(), SdkConfig.defaults(), null, eventBus
        );
        assertDoesNotThrow(agent::stop);
    }

    @Test
    void runWithSystemPrompt() {
        SdkAgent agent = new SdkAgent(
                null, Collections.emptyMap(), SdkConfig.defaults(), "You are helpful", eventBus
        );
        String result = agent.run("hello");
        assertEquals("", result);
        assertEquals(AgentState.STOPPED, agent.getState());
    }

    @Test
    void sdkConfigOverridesDefaults() {
        SdkConfig config = new SdkConfig(5, 30, null);
        SdkAgent agent = new SdkAgent(
                null, Collections.emptyMap(), config, null, eventBus
        );
        String result = agent.run("test");
        assertEquals("", result);
    }
}
