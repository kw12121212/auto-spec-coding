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
     * Returns the skill route for the given skill name, or null if no route is configured.
     */
    SkillRoute route(String skillName);

    /**
     * Registers a skill route mapping a skill name to a provider and optional model override.
     */
    void addSkillRoute(String skillName, SkillRoute route);

    /**
     * Closes all registered providers and clears internal maps.
     */
    @Override
    void close();
}
