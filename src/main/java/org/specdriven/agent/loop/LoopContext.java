package org.specdriven.agent.loop;

import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * Context passed to the scheduler for selecting the next candidate.
 *
 * @param milestoneFile         the current milestone file
 * @param milestoneGoal         the milestone's goal
 * @param plannedChanges        changes listed in the milestone
 * @param completedChangeNames  names of already-completed changes
 */
public record LoopContext(
        String milestoneFile,
        String milestoneGoal,
        List<PlannedChange> plannedChanges,
        Set<String> completedChangeNames
) {
    public LoopContext {
        plannedChanges = plannedChanges == null ? List.of() : Collections.unmodifiableList(List.copyOf(plannedChanges));
        completedChangeNames = completedChangeNames == null ? Set.of() : Collections.unmodifiableSet(Set.copyOf(completedChangeNames));
    }
}
