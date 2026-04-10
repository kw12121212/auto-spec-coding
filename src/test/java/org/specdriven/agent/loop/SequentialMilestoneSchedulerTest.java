package org.specdriven.agent.loop;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SequentialMilestoneSchedulerTest {

    @TempDir
    Path tempDir;

    private void writeIndex(String content) throws IOException {
        Files.writeString(tempDir.resolve("INDEX.md"), content);
    }

    private void writeMilestone(String filename, String content) throws IOException {
        Path milestonesDir = tempDir.resolve("milestones");
        Files.createDirectories(milestonesDir);
        Files.writeString(milestonesDir.resolve(filename), content);
    }

    @Test
    void selectsFirstPlannedChange() throws Exception {
        writeIndex("- [m1.md](milestones/m1.md) - M1 - complete\n" +
                "- [m2.md](milestones/m2.md) - M2 - proposed\n");
        writeMilestone("m1.md", "# M1\n## Status\n- Declared: complete\n## Planned Changes\n- `a` - Declared: complete - done\n");
        writeMilestone("m2.md", "# M2\n## Goal\nBuild feature\n## Status\n- Declared: proposed\n## Planned Changes\n" +
                "- `change-a` - Declared: planned - first\n- `change-b` - Declared: planned - second\n");

        SequentialMilestoneScheduler scheduler = new SequentialMilestoneScheduler(tempDir, List.of());
        LoopContext ctx = new LoopContext("", "", List.of(), Set.of());
        Optional<LoopCandidate> result = scheduler.selectNext(ctx);

        assertTrue(result.isPresent());
        assertEquals("change-a", result.get().changeName());
        assertEquals("m2.md", result.get().milestoneFile());
        assertEquals("Build feature", result.get().milestoneGoal());
    }

    @Test
    void skipsCompletedChanges() throws Exception {
        writeIndex("- [m2.md](milestones/m2.md) - M2 - proposed\n");
        writeMilestone("m2.md", "# M2\n## Goal\nGoal\n## Status\n- Declared: proposed\n## Planned Changes\n" +
                "- `change-a` - Declared: complete - done\n- `change-b` - Declared: planned - next\n");

        SequentialMilestoneScheduler scheduler = new SequentialMilestoneScheduler(tempDir, List.of());
        LoopContext ctx = new LoopContext("", "", List.of(), Set.of());
        Optional<LoopCandidate> result = scheduler.selectNext(ctx);

        assertTrue(result.isPresent());
        assertEquals("change-b", result.get().changeName());
    }

    @Test
    void skipsCompletedMilestones() throws Exception {
        writeIndex("- [m1.md](milestones/m1.md) - M1 - complete\n" +
                "- [m2.md](milestones/m2.md) - M2 - proposed\n");
        writeMilestone("m1.md", "# M1\n## Status\n- Declared: complete\n## Planned Changes\n- `x` - Declared: planned - x\n");
        writeMilestone("m2.md", "# M2\n## Goal\nGoal\n## Status\n- Declared: proposed\n## Planned Changes\n- `y` - Declared: planned - y\n");

        SequentialMilestoneScheduler scheduler = new SequentialMilestoneScheduler(tempDir, List.of());
        LoopContext ctx = new LoopContext("", "", List.of(), Set.of());
        Optional<LoopCandidate> result = scheduler.selectNext(ctx);

        assertTrue(result.isPresent());
        assertEquals("y", result.get().changeName());
    }

    @Test
    void skipsAlreadyCompletedChangeNames() throws Exception {
        writeIndex("- [m2.md](milestones/m2.md) - M2 - proposed\n");
        writeMilestone("m2.md", "# M2\n## Goal\nGoal\n## Status\n- Declared: proposed\n## Planned Changes\n" +
                "- `change-a` - Declared: planned - first\n- `change-b` - Declared: planned - second\n");

        SequentialMilestoneScheduler scheduler = new SequentialMilestoneScheduler(tempDir, List.of());
        LoopContext ctx = new LoopContext("", "", List.of(), Set.of("change-a"));
        Optional<LoopCandidate> result = scheduler.selectNext(ctx);

        assertTrue(result.isPresent());
        assertEquals("change-b", result.get().changeName());
    }

    @Test
    void returnsEmptyWhenAllComplete() throws Exception {
        writeIndex("- [m1.md](milestones/m1.md) - M1 - complete\n");
        writeMilestone("m1.md", "# M1\n## Status\n- Declared: complete\n## Planned Changes\n- `x` - Declared: complete - done\n");

        SequentialMilestoneScheduler scheduler = new SequentialMilestoneScheduler(tempDir, List.of());
        LoopContext ctx = new LoopContext("", "", List.of(), Set.of());
        assertTrue(scheduler.selectNext(ctx).isEmpty());
    }

    @Test
    void targetMilestonesFilterRestrictsScope() throws Exception {
        writeIndex("- [m1.md](milestones/m1.md) - M1 - proposed\n" +
                "- [m2.md](milestones/m2.md) - M2 - proposed\n");
        writeMilestone("m1.md", "# M1\n## Goal\nG1\n## Status\n- Declared: proposed\n## Planned Changes\n- `a` - Declared: planned - a\n");
        writeMilestone("m2.md", "# M2\n## Goal\nG2\n## Status\n- Declared: proposed\n## Planned Changes\n- `b` - Declared: planned - b\n");

        SequentialMilestoneScheduler scheduler = new SequentialMilestoneScheduler(tempDir, List.of("m2.md"));
        LoopContext ctx = new LoopContext("", "", List.of(), Set.of());
        Optional<LoopCandidate> result = scheduler.selectNext(ctx);

        assertTrue(result.isPresent());
        assertEquals("b", result.get().changeName());
    }

    @Test
    void targetMilestonesFilterExcludesAllReturnsEmpty() throws Exception {
        writeIndex("- [m1.md](milestones/m1.md) - M1 - proposed\n");
        writeMilestone("m1.md", "# M1\n## Goal\nG1\n## Status\n- Declared: proposed\n## Planned Changes\n- `a` - Declared: planned - a\n");

        SequentialMilestoneScheduler scheduler = new SequentialMilestoneScheduler(tempDir, List.of("nonexistent.md"));
        LoopContext ctx = new LoopContext("", "", List.of(), Set.of());
        assertTrue(scheduler.selectNext(ctx).isEmpty());
    }

    @Test
    void noMilestoneFilesReturnsEmpty() throws Exception {
        writeIndex("");
        SequentialMilestoneScheduler scheduler = new SequentialMilestoneScheduler(tempDir, List.of());
        LoopContext ctx = new LoopContext("", "", List.of(), Set.of());
        assertTrue(scheduler.selectNext(ctx).isEmpty());
    }
}
