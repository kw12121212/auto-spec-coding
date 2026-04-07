package org.specdriven.agent.permission;

/**
 * Result of evaluating whether an operation may proceed.
 */
public enum PermissionDecision {
    ALLOW,
    DENY,
    CONFIRM
}
