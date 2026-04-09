package org.specdriven.agent.tool;

/**
 * Type of readiness probe to perform on a server-class tool.
 */
public enum ProbeType {
    /** TCP connect probe — checks if a TCP connection can be established. */
    TCP,
    /** HTTP GET probe — checks if an HTTP endpoint returns the expected status code. */
    HTTP
}
