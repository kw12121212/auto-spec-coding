package org.specdriven.agent.agent;

import java.util.Set;

/**
 * Manages named {@link LlmProvider} instances with default fallback and skill-based routing.
 */
public interface LlmProviderRegistry extends AutoCloseable {

    /**
     * Registers a provider under the given name.
     *
     * @throws IllegalArgumentException if name or provider is null, or name is already registered
     */
    void register(String name, LlmProvider provider);

    /**
     * Returns the provider registered under the given name.
     *
     * @throws IllegalArgumentException if no provider is registered under this name
     */
    LlmProvider provider(String name);

    /**
     * Returns the default provider.
     * If no explicit default is set, returns the first registered provider.
     *
     * @throws IllegalStateException if the registry is empty
     */
    LlmProvider defaultProvider();

    /**
     * Returns the active runtime snapshot for the default scope.
     */
    default LlmConfigSnapshot defaultSnapshot() {
        throw new UnsupportedOperationException("Runtime snapshots are not supported by this registry");
    }

    /**
     * Returns the active runtime snapshot for the given session, falling back to the default scope.
     */
    default LlmConfigSnapshot snapshot(String sessionId) {
        return defaultSnapshot();
    }

    /**
     * Returns the set of registered provider names.
     */
    Set<String> providerNames();

    /**
     * Removes and closes the provider registered under the given name.
     *
     * @throws IllegalArgumentException if no provider is registered under this name
     */
    void remove(String name);

    /**
     * Sets the default provider by name.
     *
     * @throws IllegalArgumentException if no provider is registered under this name
     */
    void setDefault(String name);

    /**
     * Atomically replaces the default runtime snapshot used by future requests.
     */
    default void replaceDefaultSnapshot(LlmConfigSnapshot snapshot) {
        throw new UnsupportedOperationException("Runtime snapshots are not supported by this registry");
    }

    /**
     * Atomically replaces the runtime snapshot for a session.
     */
    default void replaceSessionSnapshot(String sessionId, LlmConfigSnapshot snapshot) {
        throw new UnsupportedOperationException("Runtime snapshots are not supported by this registry");
    }

    /**
     * Parses and applies a session-scoped {@code SET LLM} SQL statement as one atomic runtime snapshot replacement.
     */
    default LlmConfigSnapshot applySetLlmStatement(String sessionId, String sql) {
        throw new UnsupportedOperationException("Runtime snapshots are not supported by this registry");
    }

    /**
     * Clears any session-specific snapshot override so the session falls back to the default snapshot.
     */
    default void clearSessionSnapshot(String sessionId) {
        throw new UnsupportedOperationException("Runtime snapshots are not supported by this registry");
    }

    /**
     * Returns the skill route for the given skill name, or null if no route is configured.
     */
    SkillRoute route(String skillName);

    /**
     * Registers a skill route mapping a skill name to a provider and optional model override.
     */
    void addSkillRoute(String skillName, SkillRoute route);

    /**
     * Creates a client that resolves the effective runtime snapshot for each request.
     */
    default LlmClient createClientForSession(String sessionId) {
        throw new UnsupportedOperationException("Runtime snapshots are not supported by this registry");
    }

    /**
     * Closes all registered providers and clears internal maps.
     */
    @Override
    void close();
}
