package org.specdriven.agent.loop;

import java.util.EnumSet;
import java.util.List;

/**
 * Durable resume boundary for an incomplete loop iteration.
 *
 * @param changeName             selected change identifier
 * @param milestoneFile          milestone file containing the selected change
 * @param milestoneGoal          selected milestone goal, empty when unavailable
 * @param plannedChangeSummary   selected planned change summary, empty when unavailable
 * @param completedPhases        phases completed successfully before interruption
 */
public record LoopPhaseCheckpoint(
        String changeName,
        String milestoneFile,
        String milestoneGoal,
        String plannedChangeSummary,
        List<PipelinePhase> completedPhases
) {
    public LoopPhaseCheckpoint {
        if (changeName == null) {
            throw new NullPointerException("changeName must not be null");
        }
        if (milestoneFile == null) {
            throw new NullPointerException("milestoneFile must not be null");
        }
        milestoneGoal = milestoneGoal == null ? "" : milestoneGoal;
        plannedChangeSummary = plannedChangeSummary == null ? "" : plannedChangeSummary;
        completedPhases = orderedUnique(completedPhases);
    }

    public LoopPhaseCheckpoint(LoopCandidate candidate, List<PipelinePhase> completedPhases) {
        this(candidate.changeName(), candidate.milestoneFile(), candidate.milestoneGoal(),
                candidate.plannedChangeSummary(), completedPhases);
    }

    public LoopCandidate candidate() {
        return new LoopCandidate(changeName, milestoneFile, milestoneGoal, plannedChangeSummary);
    }

    private static List<PipelinePhase> orderedUnique(List<PipelinePhase> phases) {
        if (phases == null || phases.isEmpty()) {
            return List.of();
        }
        EnumSet<PipelinePhase> phaseSet = EnumSet.noneOf(PipelinePhase.class);
        phaseSet.addAll(phases);
        return PipelinePhase.ordered().stream()
                .filter(phaseSet::contains)
                .toList();
    }
}
