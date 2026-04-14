package org.specdriven.agent.loop;

/**
 * A candidate change selected by the scheduler for execution.
 *
 * @param changeName    the change identifier
 * @param milestoneFile the milestone file containing this change
 * @param milestoneGoal the milestone's goal description
 * @param plannedChangeSummary the planned change's summary from the milestone
 */
public record LoopCandidate(
        String changeName,
        String milestoneFile,
        String milestoneGoal,
        String plannedChangeSummary
) {
    public LoopCandidate(String changeName, String milestoneFile, String milestoneGoal) {
        this(changeName, milestoneFile, milestoneGoal, "");
    }

    public LoopCandidate {
        if (changeName == null) throw new NullPointerException("changeName must not be null");
        if (milestoneFile == null) throw new NullPointerException("milestoneFile must not be null");
        plannedChangeSummary = plannedChangeSummary == null ? "" : plannedChangeSummary;
    }
}
