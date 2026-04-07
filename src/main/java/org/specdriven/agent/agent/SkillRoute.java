package org.specdriven.agent.agent;

/**
 * Immutable mapping from a skill name to a specific provider and optional model override.
 *
 * @param providerName the name of the target provider (non-null)
 * @param modelOverride optional model name to override the provider's default (nullable)
 */
public record SkillRoute(String providerName, String modelOverride) {

    public SkillRoute {
        if (providerName == null || providerName.isBlank()) {
            throw new IllegalArgumentException("providerName must not be null or blank");
        }
    }
}
