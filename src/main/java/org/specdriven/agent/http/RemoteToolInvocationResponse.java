package org.specdriven.agent.http;

/**
 * Payload returned by a remote tool callback.
 */
public record RemoteToolInvocationResponse(
        boolean success,
        String output,
        String error
) {}
