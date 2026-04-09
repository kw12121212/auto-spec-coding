package org.specdriven.agent.tool;

/**
 * A background tool that launches a server-class process requiring readiness probing.
 * <p>
 * Tools implementing this interface declare a probe configuration via
 * {@link #getReadyProbe()}, which the process manager uses to wait for the
 * server to become ready after launch.
 */
public interface ServerTool extends BackgroundTool {

    /**
     * Returns the readiness probe configuration for this server tool.
     *
     * @return the probe descriptor, never null
     */
    ReadyProbe getReadyProbe();
}
