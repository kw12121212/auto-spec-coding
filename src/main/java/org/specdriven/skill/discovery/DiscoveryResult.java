package org.specdriven.skill.discovery;

import java.util.List;

/**
 * Summary of a skill auto-discovery run.
 */
public record DiscoveryResult(int registeredCount, int failedCount, List<SkillDiscoveryError> errors) {}
