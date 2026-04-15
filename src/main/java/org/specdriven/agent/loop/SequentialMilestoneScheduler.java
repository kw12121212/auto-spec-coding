package org.specdriven.agent.loop;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reads roadmap INDEX.md and milestone files from disk on every selectNext() call.
 * Selects the first non-complete milestone's first planned change not yet completed.
 */
public class SequentialMilestoneScheduler implements LoopScheduler {

    private final Path roadmapDir;
    private final List<String> targetMilestones;

    private static final Pattern MILESTONE_LINE = Pattern.compile(
            "^-\\s+\\[([^\\]]+)\\]\\([^)]+\\)\\s+-\\s+.*$");
    private static final Pattern PLANNED_CHANGE_LINE = Pattern.compile(
            "^-\\s+`([^`]+)`\\s+-\\s+Declared:\\s+(\\S+)\\s*-\\s*(.*)$");
    private static final Pattern GOAL_LINE = Pattern.compile(
            "^##\\s+Goal\\s*$");
    private static final Pattern STATUS_LINE = Pattern.compile(
            "^-\\s+Declared:\\s+(\\S+)\\s*$");

    /**
     * @param roadmapDir       path to the .spec-driven/roadmap/ directory
     * @param targetMilestones optional filter; empty means scan all milestones
     */
    public SequentialMilestoneScheduler(Path roadmapDir, List<String> targetMilestones) {
        this.roadmapDir = roadmapDir;
        this.targetMilestones = targetMilestones == null ? List.of() : List.copyOf(targetMilestones);
    }

    @Override
    public Optional<LoopCandidate> selectNext(LoopContext context) {
        Path indexFile = roadmapDir.resolve("INDEX.md");
        List<String> milestoneFiles = parseIndex(indexFile);

        for (String milestoneFile : milestoneFiles) {
            // Apply target filter
            if (!targetMilestones.isEmpty() && !targetMilestones.contains(milestoneFile)) {
                continue;
            }

            Path milestonePath = roadmapDir.resolve("milestones").resolve(milestoneFile);
            MilestoneData data = parseMilestone(milestonePath);

            // Skip complete milestones
            if ("complete".equalsIgnoreCase(data.status)) {
                continue;
            }

            // Find first planned change not in completedChangeNames
            for (PlannedChange change : data.plannedChanges) {
                if (!"planned".equalsIgnoreCase(change.status())) {
                    continue;
                }
                if (context.completedChangeNames().contains(change.name())) {
                    continue;
                }
                return Optional.of(new LoopCandidate(
                        change.name(),
                        milestoneFile,
                        data.goal,
                        change.summary()
                ));
            }
        }

        return Optional.empty();
    }

    /**
     * Summarizes roadmap progress using the same index and milestone parsing rules as scheduling.
     */
    public static RoadmapSummary summarizeRoadmap(Path roadmapDir) {
        SequentialMilestoneScheduler scheduler = new SequentialMilestoneScheduler(roadmapDir, List.of());
        List<String> milestoneFiles = scheduler.parseIndex(roadmapDir.resolve("INDEX.md"));
        int completeMilestones = 0;
        int activeMilestones = 0;
        int plannedChanges = 0;
        int completeChanges = 0;

        for (String milestoneFile : milestoneFiles) {
            MilestoneData data = scheduler.parseMilestone(roadmapDir.resolve("milestones").resolve(milestoneFile));
            if ("complete".equalsIgnoreCase(data.status())) {
                completeMilestones++;
            } else {
                activeMilestones++;
            }
            for (PlannedChange change : data.plannedChanges()) {
                if ("complete".equalsIgnoreCase(change.status())) {
                    completeChanges++;
                } else if ("planned".equalsIgnoreCase(change.status())) {
                    plannedChanges++;
                }
            }
        }

        Optional<LoopCandidate> nextPlannedChange = scheduler.selectNext(new LoopContext("", "", List.of(), Set.of()));
        return new RoadmapSummary(
                milestoneFiles.size(),
                completeMilestones,
                activeMilestones,
                plannedChanges,
                completeChanges,
                nextPlannedChange
        );
    }

    /**
     * Parses INDEX.md to extract milestone file names in order.
     */
    List<String> parseIndex(Path indexFile) {
        List<String> files = new ArrayList<>();
        try {
            List<String> lines = Files.readAllLines(indexFile);
            for (String line : lines) {
                Matcher m = MILESTONE_LINE.matcher(line);
                if (m.matches()) {
                    files.add(m.group(1));
                }
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to read roadmap INDEX.md: " + indexFile, e);
        }
        return files;
    }

    /**
     * Parses a single milestone file to extract goal, status, and planned changes.
     */
    MilestoneData parseMilestone(Path milestonePath) {
        String goal = "";
        String status = "";
        List<PlannedChange> changes = new ArrayList<>();

        try {
            List<String> lines = Files.readAllLines(milestonePath);
            String section = "";
            for (String line : lines) {
                if (GOAL_LINE.matcher(line).matches()) {
                    section = "goal";
                    continue;
                }
                if (line.startsWith("## ")) {
                    section = line.strip();
                    continue;
                }

                if ("goal".equals(section) && goal.isEmpty() && !line.isBlank()) {
                    goal = line.strip();
                }

                if ("## Status".equals(section)) {
                    Matcher sm = STATUS_LINE.matcher(line);
                    if (sm.matches()) {
                        status = sm.group(1);
                    }
                }

                if ("## Planned Changes".equals(section)) {
                    Matcher cm = PLANNED_CHANGE_LINE.matcher(line);
                    if (cm.matches()) {
                        changes.add(new PlannedChange(cm.group(1), cm.group(2), cm.group(3).strip()));
                    }
                }
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to read milestone file: " + milestonePath, e);
        }

        return new MilestoneData(goal, status, changes);
    }

    /**
     * Internal parsed milestone data.
     */
    record MilestoneData(String goal, String status, List<PlannedChange> plannedChanges) {
    }

    public record RoadmapSummary(
            int totalMilestones,
            int completeMilestones,
            int activeMilestones,
            int plannedChanges,
            int completeChanges,
            Optional<LoopCandidate> nextPlannedChange
    ) {
        public RoadmapSummary {
            nextPlannedChange = nextPlannedChange == null ? Optional.empty() : nextPlannedChange;
        }
    }
}
