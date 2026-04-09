package org.specdriven.skill.discovery;

import java.nio.file.Path;

/**
 * Describes a per-skill failure during auto-discovery.
 */
public record SkillDiscoveryError(Path path, String errorMessage) {}
