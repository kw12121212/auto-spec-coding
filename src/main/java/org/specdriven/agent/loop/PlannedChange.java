package org.specdriven.agent.loop;

/**
 * A change listed in a milestone file.
 *
 * @param name    the change identifier
 * @param status  declared status (e.g. "planned", "complete")
 * @param summary short description
 */
public record PlannedChange(
        String name,
        String status,
        String summary
) {
    public PlannedChange {
        if (name == null) throw new NullPointerException("name must not be null");
    }
}
