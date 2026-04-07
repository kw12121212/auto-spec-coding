package org.specdriven.agent.agent;

/**
 /**
 * Callback interface for streaming LLM response handling.
 Reserved for subsequent streaming implementation.
 */
public interface LlmStreamCallback {
    /**
     * Called when a new token is received during streaming.
     */
    void onToken(String token);

    /**
     * Called when the streaming response is complete.
         */
    void onComplete(LlmResponse response);    /**
     * Called when a streaming response encounters an error.     */
    void onError(Exception e);
 }