package org.specdriven.agent.tool;

/**
 * Strategy interface for probing server readiness.
 * <p>
 * Implementations attempt to connect to a server endpoint and return true
 * if the server is ready, false otherwise. Probe failures MUST return false,
 * not throw checked exceptions.
 */
public interface ProbeStrategy {

    /**
     * Probes the server described by the given probe configuration.
     *
     * @param probe the probe descriptor
     * @return true if the server is ready, false otherwise
     */
    boolean probe(ReadyProbe probe);
}
