package org.specdriven.agent.interactive;

/**
 * Observable lifecycle states of an interactive session.
 *
 * <ul>
 *   <li>{@link #NEW}    – session created but not yet started</li>
 *   <li>{@link #ACTIVE} – session started and accepting input</li>
 *   <li>{@link #CLOSED} – session closed; no further interaction possible</li>
 *   <li>{@link #ERROR}  – session encountered a terminal failure; rejects further input</li>
 * </ul>
 */
public enum InteractiveSessionState {
    NEW,
    ACTIVE,
    CLOSED,
    ERROR
}
