package org.specdriven.cli;

import org.specdriven.agent.json.JsonWriter;
import org.specdriven.agent.http.ServiceRuntimeLauncher;
import org.specdriven.agent.http.ServiceRuntimeLauncher.ServiceRuntimeException;
import org.specdriven.sdk.PlatformConfig;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public final class SpecDrivenCliMain {

    private static final List<String> DECLARED_ROADMAP_STATUSES = List.of("proposed", "active", "blocked", "complete");
    private static final List<String> DECLARED_PLANNED_CHANGE_STATUSES = List.of("planned", "complete");
    private static final String INIT_CONFIG_YAML = String.join("\n",
            "schema: spec-driven",
            "context: |",
            "  [Project context — populated by user, injected into skill prompts]",
            "rules:",
            "  specs:",
            "    - Describe observable behavior only — no implementation details, technology",
            "      choices, or internal structure",
            "    - MUST = required with no exceptions; SHOULD = default unless explicitly",
            "      justified; MAY = genuinely optional",
            "    - Each requirement must be independently verifiable from outside the system",
            "  change:",
            "    - Implement only what is in scope in proposal.md — if scope needs to expand,",
            "      use /spec-driven-modify first, never expand silently",
            "    - When a requirement or task is ambiguous, ask the user before proceeding —",
            "      do not assume or guess",
            "    - Delta specs must reflect what was actually built, not the original plan",
            "    - Mark tasks [x] immediately upon completion — never batch at the end",
            "    - Every change must include test tasks (lint + unit tests at minimum)",
            "  code:",
            "    - Read existing code before modifying it",
            "    - Implement only what the current task requires — no speculative features",
            "    - No abstractions for hypothetical future needs (YAGNI)",
            "  test:",
            "    - Tests must verify observable behavior described in specs, not internal",
            "      implementation details",
            "    - Each test must be independent — no shared mutable state between tests",
            "    - Prefer real dependencies over mocks for code the project owns",
            "# fileMatch:              # per-pattern rules applied in addition to global rules above",
            "#   - pattern: \"**/*.test.*\"",
            "#     rules:",
            "#       - Tests must cover happy path, error cases, and edge cases",
            "");
    private static final String INIT_INDEX_MD = "# Specs Index\n";
    private static final String INIT_README_MD = String.join("\n",
            "# Specs",
            "",
            "Specs describe the current state of the system — what it does, not how it was built.",
            "",
            "## Format",
            "",
            "```markdown",
            "### Requirement: <name>",
            "The system MUST/SHOULD/MAY <observable behavior>.",
            "",
            "#### Scenario: <name>",
            "- GIVEN <precondition>",
            "- WHEN <action>",
            "- THEN <expected outcome>",
            "```",
            "",
            "**Keywords**: MUST = required, SHOULD = recommended, MAY = optional (RFC 2119).",
            "",
            "## Organization",
            "",
            "Group specs by domain area. Use kebab-case directory names (e.g. `core/`, `api/`, `auth/`).",
            "",
            "## Conventions",
            "",
            "- Write in present tense (\"the system does X\")",
            "- Describe observable behavior, not implementation details",
            "- Keep each spec focused on one area",
            "");
    private static final String INIT_ROADMAP_INDEX_MD = "# Roadmap Index\n\n## Milestones\n";
    private static final String DEFAULT_MAINTENANCE_CHANGE_PREFIX = "maintenance";
    private static final String DEFAULT_MAINTENANCE_BRANCH_PREFIX = "maintenance";
    private static final String DEFAULT_MAINTENANCE_COMMIT_PREFIX = "chore: maintenance";
    private static final Pattern OPEN_QUESTION_PATTERN = Pattern.compile("^\\s*-\\s*\\[ \\]\\s+Q:", Pattern.CASE_INSENSITIVE);
    private static final Pattern CHECKBOX_COMPLETE_PATTERN = Pattern.compile("^\\s*-\\s*\\[x\\]\\s+", Pattern.CASE_INSENSITIVE);
    private static final Pattern CHECKBOX_INCOMPLETE_PATTERN = Pattern.compile("^\\s*-\\s*\\[ \\]\\s+", Pattern.CASE_INSENSITIVE);
    private static final List<MigrationTool> SUPPORTED_MIGRATION_TOOLS = List.of(
            new MigrationTool("claude", ".claude", ".claude/skills", ".claude/commands"),
            new MigrationTool("opencode", ".opencode", ".opencode/skills", ".opencode/commands")
    );

    private final Path workingDir;
    private final PrintStream out;
    private final PrintStream err;

    private SpecDrivenCliMain(Path workingDir, PrintStream out, PrintStream err) {
        this.workingDir = workingDir.toAbsolutePath().normalize();
        this.out = out;
        this.err = err;
    }

    public static void main(String[] args) {
        int exitCode = run(args, Path.of("").toAbsolutePath(), System.out, System.err);
        if (exitCode != 0) {
            System.exit(exitCode);
        }
    }

    static int run(String[] args, Path workingDir, PrintStream out, PrintStream err) {
        return new SpecDrivenCliMain(workingDir, out, err).dispatch(Arrays.asList(args));
    }

    private int dispatch(List<String> argv) {
        if (argv.isEmpty()) {
            printUsage();
            return 1;
        }
        String command = argv.get(0);
        List<String> args = argv.subList(1, argv.size());
        return switch (command) {
            case "propose" -> propose(args);
            case "modify" -> modify(args);
            case "apply" -> apply(args);
            case "verify" -> verify(args);
            case "verify-roadmap" -> verifyRoadmap(args);
            case "roadmap-status" -> roadmapStatus(args);
            case "archive" -> archive(args);
            case "cancel" -> cancel(args);
            case "init" -> init(args);
            case "run-maintenance" -> runMaintenance(args);
            case "migrate" -> migrate(args);
            case "service-runtime" -> serviceRuntime(args);
            case "list" -> list();
            default -> {
                printUsage();
                yield 1;
            }
        };
    }

    private void printUsage() {
        err.println("Usage: spec-driven <command> [args]");
        err.println("Commands: propose, modify, apply, verify, verify-roadmap, roadmap-status, archive, cancel, init, run-maintenance, migrate, service-runtime, list");
    }

    private int serviceRuntime(List<String> args) {
        ServiceRuntimeArgs parsed;
        try {
            parsed = parseServiceRuntimeArgs(args);
        } catch (IllegalArgumentException e) {
            out.println(ServiceRuntimeLauncher.failureJson("invalid_config", e.getMessage()));
            return 1;
        }

        ServiceRuntimeLauncher launcher = new ServiceRuntimeLauncher();
        try (ServiceRuntimeLauncher.RuntimeHandle handle = launcher.start(parsed.toOptions())) {
            out.println(handle.startupResult().toJson());
            if (!parsed.exitAfterStart()) {
                handle.await();
            }
            return 0;
        } catch (ServiceRuntimeException e) {
            out.println(ServiceRuntimeLauncher.failureJson(e.errorCode(), safeMessage(e)));
            return 1;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            out.println(ServiceRuntimeLauncher.failureJson("interrupted", "Service runtime interrupted"));
            return 1;
        }
    }

    private ServiceRuntimeArgs parseServiceRuntimeArgs(List<String> args) {
        Path servicesSql = null;
        String host = "127.0.0.1";
        int port = 8080;
        PlatformConfig defaults = PlatformConfig.defaults();
        String jdbcUrl = defaults.jdbcUrl();
        Path compileCachePath = defaults.compileCachePath();
        Set<String> apiKeys = new LinkedHashSet<>();
        boolean exitAfterStart = false;

        for (int i = 0; i < args.size(); i++) {
            String arg = args.get(i);
            switch (arg) {
                case "--services-sql" -> servicesSql = Path.of(requireOptionValue(args, ++i, arg));
                case "--host" -> host = requireOptionValue(args, ++i, arg);
                case "--port" -> port = parsePort(requireOptionValue(args, ++i, arg));
                case "--jdbc-url" -> jdbcUrl = requireOptionValue(args, ++i, arg);
                case "--compile-cache-path" -> compileCachePath = Path.of(requireOptionValue(args, ++i, arg));
                case "--api-key" -> apiKeys.add(requireOptionValue(args, ++i, arg));
                case "--exit-after-start" -> exitAfterStart = true;
                default -> throw new IllegalArgumentException("Unknown service-runtime option: " + arg);
            }
        }

        if (servicesSql == null) {
            throw new IllegalArgumentException("Missing required option: --services-sql");
        }
        return new ServiceRuntimeArgs(servicesSql, host, port, jdbcUrl, compileCachePath,
                Set.copyOf(apiKeys), exitAfterStart);
    }

    private String requireOptionValue(List<String> args, int index, String option) {
        if (index >= args.size()) {
            throw new IllegalArgumentException("Missing value for " + option);
        }
        String value = args.get(index);
        if (value == null || value.isBlank() || value.startsWith("--")) {
            throw new IllegalArgumentException("Missing value for " + option);
        }
        return value;
    }

    private int parsePort(String raw) {
        try {
            return Integer.parseInt(raw);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid --port: " + raw);
        }
    }

    private String safeMessage(Exception e) {
        return e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
    }

    private int propose(List<String> args) {
        String name = requireName("propose", args);
        if (name == null) {
            return 1;
        }
        if (!name.matches("^[a-z0-9]+(-[a-z0-9]+)*$")) {
            err.println("Error: name must be kebab-case (e.g. my-feature)");
            return 1;
        }
        Path dir = changeDir(name);
        if (Files.exists(dir)) {
            err.println("Error: change '" + name + "' already exists at " + displayPath(dir));
            return 1;
        }
        try {
            Files.createDirectories(dir.resolve("specs"));
            Files.writeString(dir.resolve("proposal.md"), """
                    # %s

                    ## What

                    [Describe what this change does]

                    ## Why

                    [Describe the motivation and context]

                    ## Scope

                    [List what is in scope and out of scope]

                    ## Unchanged Behavior

                    Behaviors that must not change as a result of this change (leave blank if nothing is at risk):
                    """.formatted(name), StandardCharsets.UTF_8);
            Files.writeString(dir.resolve("design.md"), """
                    # Design: %s

                    ## Approach

                    [Describe the implementation approach]

                    ## Key Decisions

                    [List significant decisions and their rationale]

                    ## Alternatives Considered

                    [Describe alternatives that were ruled out]
                    """.formatted(name), StandardCharsets.UTF_8);
            Files.writeString(dir.resolve("tasks.md"), """
                    # Tasks: %s

                    ## Implementation

                    - [ ] Task 1
                    - [ ] Task 2
                    - [ ] Task 3

                    ## Testing

                    - [ ] Replace with the repo's lint or validation command
                    - [ ] Replace with the repo's unit test command

                    ## Verification

                    - [ ] Verify implementation matches proposal
                    """.formatted(name), StandardCharsets.UTF_8);
            Files.writeString(dir.resolve("questions.md"), """
                    # Questions: %s

                    ## Open

                    <!-- Add open questions here using the format below -->
                    <!-- - [ ] Q: <question text> -->
                    <!--   Context: <why this matters / what depends on the answer> -->

                    ## Resolved

                    <!-- Resolved questions are moved here with their answers -->
                    <!-- - [x] Q: <question text> -->
                    <!--   Context: <why this matters> -->
                    <!--   A: <answer from human> -->
                    """.formatted(name), StandardCharsets.UTF_8);
        } catch (IOException e) {
            err.println("Error: failed to create change '" + name + "': " + e.getMessage());
            return 1;
        }
        out.println("Created change: " + displayPath(dir));
        out.println("  " + displayPath(dir.resolve("proposal.md")));
        out.println("  " + displayPath(dir.resolve("specs")) + "/ (populate to mirror .spec-driven/specs/ structure)");
        out.println("  " + displayPath(dir.resolve("design.md")));
        out.println("  " + displayPath(dir.resolve("tasks.md")));
        out.println("  " + displayPath(dir.resolve("questions.md")));
        return 0;
    }

    private int modify(List<String> args) {
        if (args.isEmpty()) {
            Path changesDir = changesDir();
            if (!Files.isDirectory(changesDir)) {
                out.println("No .spec-driven/changes/ directory found.");
                return 0;
            }
            List<String> changes = listDirectories(changesDir).stream()
                    .filter(name -> !"archive".equals(name))
                    .toList();
            if (changes.isEmpty()) {
                out.println("No active changes.");
            } else {
                out.println("Active changes:");
                for (String change : changes) {
                    out.println("  " + change + "    " + getStatus(change));
                }
            }
            return 0;
        }
        String name = args.get(0);
        Path dir = changeDir(name);
        if (!Files.exists(dir)) {
            err.println("Error: change '" + name + "' not found at " + displayPath(dir));
            return 1;
        }
        out.println("Artifacts for '" + name + "':");
        for (String artifact : List.of("proposal.md", "design.md", "tasks.md", "questions.md")) {
            Path artifactPath = dir.resolve(artifact);
            out.println("  " + displayPath(artifactPath) + (Files.exists(artifactPath) ? "" : " (missing)"));
        }
        Path specsDir = dir.resolve("specs");
        List<String> specFiles = findMdFiles(specsDir);
        if (specFiles.isEmpty()) {
            out.println("  " + displayPath(specsDir) + "/ (empty)");
        } else {
            for (String file : specFiles) {
                out.println("  " + displayPath(specsDir.resolve(file)));
            }
        }
        return 0;
    }

    private int apply(List<String> args) {
        String name = requireName("apply", args);
        if (name == null) {
            return 1;
        }
        Path tasksPath = changeDir(name).resolve("tasks.md");
        if (!Files.exists(tasksPath)) {
            err.println("Error: tasks.md not found at " + displayPath(tasksPath));
            return 1;
        }
        List<Map<String, Object>> tasks = new ArrayList<>();
        try {
            for (String line : Files.readAllLines(tasksPath, StandardCharsets.UTF_8)) {
                boolean complete = CHECKBOX_COMPLETE_PATTERN.matcher(line).find();
                boolean incomplete = CHECKBOX_INCOMPLETE_PATTERN.matcher(line).find();
                if (complete || incomplete) {
                    Map<String, Object> task = new LinkedHashMap<>();
                    task.put("text", line.replaceFirst("^\\s*-\\s*\\[[x ]\\]\\s+", "").trim());
                    task.put("complete", complete);
                    tasks.add(task);
                }
            }
        } catch (IOException e) {
            err.println("Error: failed to read tasks: " + e.getMessage());
            return 1;
        }
        long completeCount = tasks.stream().filter(task -> Boolean.TRUE.equals(task.get("complete"))).count();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("total", tasks.size());
        result.put("complete", completeCount);
        result.put("remaining", tasks.size() - completeCount);
        result.put("tasks", tasks);
        printJson(result);
        return 0;
    }

    private int verify(List<String> args) {
        String name = requireName("verify", args);
        if (name == null) {
            return 1;
        }
        VerificationResult result = verifyChangeArtifactsDetailed(name);
        printJson(result.toMap());
        return result.valid ? 0 : 1;
    }

    private int roadmapStatus(List<String> args) {
        Path targetDir = resolveTargetDir(args);
        List<String> warnings = new ArrayList<>();
        List<String> errors = new ArrayList<>();
        List<Map<String, Object>> milestones = new ArrayList<>();
        Path specDir = targetDir.resolve(".spec-driven");
        Path milestonesDir = specDir.resolve("roadmap").resolve("milestones");
        if (!Files.isDirectory(specDir)) {
            errors.add(".spec-driven/ not found in " + targetDir);
            printJson(mapOf("valid", false, "warnings", warnings, "errors", errors, "milestones", milestones));
            return 1;
        }
        if (!Files.isDirectory(milestonesDir)) {
            errors.add("Missing roadmap milestones directory: .spec-driven/roadmap/milestones/");
            printJson(mapOf("valid", false, "warnings", warnings, "errors", errors, "milestones", milestones));
            return 1;
        }
        List<String> milestoneFiles = findMdFiles(milestonesDir);
        if (milestoneFiles.isEmpty()) {
            warnings.add("roadmap/milestones/ is empty");
            printJson(mapOf("valid", true, "warnings", warnings, "errors", errors, "milestones", milestones));
            return 0;
        }
        List<String> requiredSections = List.of("Goal", "In Scope", "Out of Scope", "Done Criteria", "Planned Changes",
                "Dependencies", "Risks", "Status", "Notes");
        for (String file : milestoneFiles) {
            Path filePath = milestonesDir.resolve(file);
            String content = readString(filePath);
            Map<String, List<String>> sections = readLevel2Sections(content);
            List<String> missingSections = requiredSections.stream().filter(section -> !sections.containsKey(section)).toList();
            if (!missingSections.isEmpty()) {
                errors.add("roadmap/milestones/" + file + " is missing required sections: " + String.join(", ", missingSections));
                continue;
            }
            String goal = firstNonEmptyLine(sections.get("Goal"));
            ParsedStatus parsedStatus = parseDeclaredRoadmapStatus(sections.get("Status"));
            if (parsedStatus.declaredStatus == null) {
                errors.add("roadmap/milestones/" + file + " has invalid status: " + parsedStatus.error);
                continue;
            }
            List<PlannedChangeEntry> entries = readPlannedChangeEntries(sections.get("Planned Changes"));
            String plannedChangeError = validatePlannedChangeLines(sections.get("Planned Changes"));
            if (plannedChangeError != null) {
                errors.add("roadmap/milestones/" + file + " has invalid planned change entries: " + plannedChangeError);
                continue;
            }
            List<String> plannedStates = readPlannedChangeStates(specDir, entries.stream().map(entry -> entry.name).toList());
            List<Map<String, Object>> plannedChanges = new ArrayList<>();
            for (int i = 0; i < entries.size(); i++) {
                PlannedChangeEntry entry = entries.get(i);
                String derivedStatus = derivePlannedChangeDeclaredStatus(plannedStates.get(i));
                List<String> mismatches = new ArrayList<>();
                if (!entry.declaredStatus.equals(derivedStatus)) {
                    mismatches.add("declared planned change status '" + entry.declaredStatus
                            + "' does not match derived planned change status '" + derivedStatus + "'");
                }
                plannedChanges.add(mapOf(
                        "name", entry.name,
                        "declaredStatus", entry.declaredStatus,
                        "state", plannedStates.get(i),
                        "derivedStatus", derivedStatus,
                        "mismatches", mismatches
                ));
            }
            String derivedMilestoneStatus = deriveMilestoneStatus(plannedStates);
            List<String> mismatches = new ArrayList<>();
            if (!parsedStatus.declaredStatus.equals(derivedMilestoneStatus)) {
                mismatches.add("declared status '" + parsedStatus.declaredStatus + "' does not match derived status '" + derivedMilestoneStatus + "'");
            }
            milestones.add(mapOf(
                    "file", file,
                    "goal", goal,
                    "declaredStatus", parsedStatus.declaredStatus,
                    "derivedStatus", derivedMilestoneStatus,
                    "plannedChanges", plannedChanges,
                    "mismatches", mismatches
            ));
        }
        boolean valid = errors.isEmpty();
        printJson(mapOf("valid", valid, "warnings", warnings, "errors", errors, "milestones", milestones));
        return valid ? 0 : 1;
    }

    private int verifyRoadmap(List<String> args) {
        Path targetDir = resolveTargetDir(args);
        List<String> warnings = new ArrayList<>();
        List<String> errors = new ArrayList<>();
        Map<String, Object> allowedStatuses = mapOf(
                "milestoneDeclaredStatuses", new ArrayList<>(DECLARED_ROADMAP_STATUSES),
                "plannedChangeDeclaredStatuses", new ArrayList<>(DECLARED_PLANNED_CHANGE_STATUSES)
        );
        List<Map<String, Object>> milestones = new ArrayList<>();
        Path specDir = targetDir.resolve(".spec-driven");
        Path roadmapDir = specDir.resolve("roadmap");
        Path milestonesDir = roadmapDir.resolve("milestones");
        if (!Files.isDirectory(specDir)) {
            errors.add(".spec-driven/ not found in " + targetDir);
            printJson(mapOf("valid", false, "warnings", warnings, "errors", errors, "allowedStatuses", allowedStatuses, "milestones", milestones));
            return 1;
        }
        if (!Files.isDirectory(milestonesDir)) {
            errors.add("Missing roadmap milestones directory: .spec-driven/roadmap/milestones/");
            printJson(mapOf("valid", false, "warnings", warnings, "errors", errors, "allowedStatuses", allowedStatuses, "milestones", milestones));
            return 1;
        }
        validateRoadmapIndex(roadmapDir, errors);
        List<String> milestoneFiles = findMdFiles(milestonesDir);
        if (milestoneFiles.isEmpty()) {
            warnings.add("roadmap/milestones/ is empty");
            printJson(mapOf("valid", true, "warnings", warnings, "errors", errors, "allowedStatuses", allowedStatuses, "milestones", milestones));
            return 0;
        }
        List<String> requiredSections = List.of("Goal", "In Scope", "Out of Scope", "Done Criteria", "Planned Changes",
                "Dependencies", "Risks", "Status", "Notes");
        for (String file : milestoneFiles) {
            String content = readString(milestonesDir.resolve(file));
            Map<String, List<String>> sections = readLevel2Sections(content);
            List<String> missingSections = requiredSections.stream().filter(section -> !sections.containsKey(section)).toList();
            if (!missingSections.isEmpty()) {
                errors.add("roadmap/milestones/" + file + " is missing required sections: " + String.join(", ", missingSections));
                continue;
            }
            int doneCriteria = countBulletItems(sections.get("Done Criteria"));
            int plannedChanges = countBulletItems(sections.get("Planned Changes"));
            ParsedStatus parsedStatus = parseDeclaredRoadmapStatus(sections.get("Status"));
            String goal = firstNonEmptyLine(sections.get("Goal"));
            String plannedChangeError = validatePlannedChangeLines(sections.get("Planned Changes"));
            if (plannedChangeError != null) {
                errors.add("roadmap/milestones/" + file + " has invalid planned change entries: " + plannedChangeError);
                continue;
            }
            if (parsedStatus.declaredStatus == null) {
                errors.add("roadmap/milestones/" + file + " has invalid status: " + parsedStatus.error);
                continue;
            }
            milestones.add(mapOf(
                    "file", file,
                    "goal", goal,
                    "doneCriteria", doneCriteria,
                    "plannedChanges", plannedChanges,
                    "status", parsedStatus.declaredStatus
            ));
            if (plannedChanges > 5) {
                errors.add("roadmap/milestones/" + file + " has " + plannedChanges + " planned changes; split it into smaller milestones");
            }
        }
        boolean valid = errors.isEmpty();
        printJson(mapOf("valid", valid, "warnings", warnings, "errors", errors, "allowedStatuses", allowedStatuses, "milestones", milestones));
        return valid ? 0 : 1;
    }

    private int archive(List<String> args) {
        String name = requireName("archive", args);
        if (name == null) {
            return 1;
        }
        Path src = requireChange(name);
        if (src == null) {
            return 1;
        }
        Path archivePath = changesDir().resolve("archive").resolve(formatLocalDate() + "-" + name);
        if (Files.exists(archivePath)) {
            err.println("Error: archive target already exists: " + displayPath(archivePath));
            return 1;
        }
        try {
            Files.createDirectories(archivePath.getParent());
            Files.move(src, archivePath);
            reconcileRoadmapAfterArchive(workingDir, name);
        } catch (IOException e) {
            err.println("Error: failed to archive change: " + e.getMessage());
            return 1;
        }
        out.println("Archived: " + displayPath(src) + " → " + displayPath(archivePath));
        return 0;
    }

    private int cancel(List<String> args) {
        String name = requireName("cancel", args);
        if (name == null) {
            return 1;
        }
        Path dir = requireChange(name);
        if (dir == null) {
            return 1;
        }
        try {
            deleteRecursively(dir);
        } catch (IOException e) {
            err.println("Error: failed to cancel change: " + e.getMessage());
            return 1;
        }
        out.println("Cancelled: " + displayPath(dir));
        return 0;
    }

    private int init(List<String> args) {
        Path targetDir = resolveTargetDir(args);
        Path specDir = targetDir.resolve(".spec-driven");
        List<String> lines = new ArrayList<>();
        try {
            ensureSpecDrivenScaffold(specDir, lines);
            regenerateIndexMd(specDir.resolve("specs"), lines);
            regenerateRoadmapIndex(specDir.resolve("roadmap"), lines);
        } catch (IOException e) {
            err.println("Error: failed to initialize .spec-driven: " + e.getMessage());
            return 1;
        }
        out.println("Initialized: " + displayPath(specDir));
        for (String line : lines) {
            out.println("  " + line);
        }
        out.println("  Edit config.yaml to add project context");
        return 0;
    }

    private int runMaintenance(List<String> args) {
        Path targetDir = resolveTargetDir(args);
        Path specDir = targetDir.resolve(".spec-driven");
        if (!Files.isDirectory(specDir)) {
            printJsonTo(err, mapOf("status", "error", "message", ".spec-driven/ not found in " + targetDir));
            return 1;
        }
        MaintenanceConfig config = loadMaintenanceConfig(targetDir);
        if (config == null) {
            return 1;
        }
        if (config.checks.isEmpty()) {
            printJson(mapOf("status", "skipped", "reason", "no-configured-checks"));
            return 0;
        }
        String activeChange = findActiveMaintenanceChange(targetDir, config.changePrefix);
        if (activeChange != null) {
            printJson(mapOf("status", "skipped", "reason", "active-maintenance-change", "change", activeChange));
            return 0;
        }
        ShellCommandResult repoCheck = runShellCommand("git rev-parse --show-toplevel", targetDir);
        if (repoCheck.status != 0) {
            printJsonTo(err, mapOf("status", "error", "message", "target is not a git repository", "stderr", repoCheck.stderr.trim()));
            return 1;
        }
        ShellCommandResult dirty = runShellCommand("git status --porcelain", targetDir);
        if (dirty.status != 0) {
            printJsonTo(err, mapOf("status", "error", "message", "failed to inspect git working tree", "stderr", dirty.stderr.trim()));
            return 1;
        }
        if (!dirty.stdout.trim().isEmpty()) {
            printJson(mapOf("status", "skipped", "reason", "dirty-working-tree"));
            return 0;
        }
        List<CheckResult> initialResults = config.checks.stream()
                .map(check -> new CheckResult(check, runShellCommand(check.command, targetDir)))
                .toList();
        List<CheckResult> failingChecks = initialResults.stream().filter(result -> result.result.status != 0).toList();
        if (failingChecks.isEmpty()) {
            printJson(mapOf("status", "clean", "checks", config.checks.stream().map(check -> check.name).toList()));
            return 0;
        }
        List<CheckResult> unfixable = failingChecks.stream().filter(result -> result.check.fixCommand == null).toList();
        if (!unfixable.isEmpty()) {
            printJson(mapOf(
                    "status", "unfixable",
                    "failedChecks", failingChecks.stream().map(result -> result.check.name).toList(),
                    "unfixableChecks", unfixable.stream().map(result -> result.check.name).toList()
            ));
            return 0;
        }
        ShellCommandResult originalBranch = runShellCommand("git branch --show-current", targetDir);
        if (originalBranch.status != 0) {
            printJsonTo(err, mapOf("status", "error", "message", "failed to resolve current branch", "stderr", originalBranch.stderr.trim()));
            return 1;
        }
        String stamp = makeMaintenanceStamp();
        String changeName = config.changePrefix + "-" + stamp;
        String branchName = config.branchPrefix + "-" + stamp;
        ShellCommandResult branchCreate = runShellCommand("git switch -c " + shellQuote(branchName), targetDir);
        if (branchCreate.status != 0) {
            printJsonTo(err, mapOf("status", "error", "message", "failed to create maintenance branch", "stderr", branchCreate.stderr.trim()));
            return 1;
        }
        try {
            seedMaintenanceChange(targetDir, changeName, branchName, failingChecks.stream().map(result -> result.check).toList(), config);
        } catch (IOException e) {
            printJson(mapOf("status", "blocked", "reason", "seed-maintenance-change-failed", "branch", branchName,
                    "change", changeName, "error", e.getMessage()));
            return 0;
        }
        String implementationTask = "Apply configured auto-fixes for the failing maintenance checks";
        String testingTask = "Re-run the configured maintenance checks and confirm they pass";
        String verificationTask = "Verify the maintenance change is valid and archive it";
        for (CheckResult failingCheck : failingChecks) {
            ShellCommandResult fixResult = runShellCommand(Objects.requireNonNull(failingCheck.check.fixCommand), targetDir);
            if (fixResult.status != 0) {
                printJson(mapOf("status", "blocked", "reason", "fix-command-failed", "branch", branchName,
                        "change", changeName, "failedCheck", failingCheck.check.name, "stderr", fixResult.stderr.trim()));
                return 0;
            }
        }
        markTaskComplete(changeDir(changeName).resolve("tasks.md"), implementationTask);
        List<CheckResult> verificationResults = config.checks.stream()
                .map(check -> new CheckResult(check, runShellCommand(check.command, targetDir)))
                .toList();
        List<CheckResult> stillFailing = verificationResults.stream().filter(result -> result.result.status != 0).toList();
        if (!stillFailing.isEmpty()) {
            printJson(mapOf("status", "blocked", "reason", "checks-still-failing", "branch", branchName,
                    "change", changeName, "failedChecks", stillFailing.stream().map(result -> result.check.name).toList()));
            return 0;
        }
        markTaskComplete(changeDir(changeName).resolve("tasks.md"), testingTask);
        VerificationResult changeVerify = verifyChangeArtifactsBasic(changeName);
        if (!changeVerify.valid) {
            printJson(mapOf("status", "blocked", "reason", "invalid-maintenance-change", "branch", branchName,
                    "change", changeName, "errors", changeVerify.errors, "warnings", changeVerify.warnings));
            return 0;
        }
        markTaskComplete(changeDir(changeName).resolve("tasks.md"), verificationTask);
        ArchiveResult archiveResult = tryArchiveChange(changeName);
        if (!archiveResult.ok) {
            printJson(mapOf("status", "blocked", "reason", "archive-failed", "branch", branchName,
                    "change", changeName, "error", archiveResult.error));
            return 0;
        }
        String commitMessage = config.commitMessagePrefix + " " + stamp;
        ShellCommandResult commitResult = runShellCommand("git add -A && git commit -m " + shellQuote(commitMessage), targetDir);
        if (commitResult.status != 0) {
            printJson(mapOf("status", "blocked", "reason", "git-commit-failed", "branch", branchName,
                    "archivePath", archiveResult.archivePath, "stderr", commitResult.stderr.trim()));
            return 0;
        }
        String branchBefore = originalBranch.stdout.trim();
        if (!branchBefore.isEmpty()) {
            ShellCommandResult switchBack = runShellCommand("git switch " + shellQuote(branchBefore), targetDir);
            if (switchBack.status != 0) {
                printJson(mapOf("status", "blocked", "reason", "restore-branch-failed", "branch", branchName,
                        "archivePath", archiveResult.archivePath, "stderr", switchBack.stderr.trim()));
                return 0;
            }
        }
        printJson(mapOf("status", "repaired", "branch", branchName, "change", changeName,
                "archivePath", archiveResult.archivePath,
                "fixedChecks", failingChecks.stream().map(result -> result.check.name).toList()));
        return 0;
    }

    private int migrate(List<String> args) {
        Path targetDir = resolveTargetDir(args);
        if (!Files.isDirectory(targetDir)) {
            err.println("Error: target directory not found: " + targetDir);
            return 1;
        }
        Path bundledSkillsDir = resolveBundledSkillsDir();
        if (bundledSkillsDir == null) {
            err.println("Error: bundled spec-driven skills not found next to this CLI");
            return 1;
        }
        List<String> bundledSkills;
        try (Stream<Path> stream = Files.list(bundledSkillsDir)) {
            bundledSkills = stream
                    .filter(Files::isDirectory)
                    .filter(path -> path.getFileName().toString().startsWith("spec-driven-"))
                    .filter(path -> Files.exists(path.resolve("SKILL.md")))
                    .map(path -> path.getFileName().toString())
                    .sorted()
                    .toList();
        } catch (IOException e) {
            err.println("Error: failed to inspect bundled skills: " + e.getMessage());
            return 1;
        }
        if (bundledSkills.isEmpty()) {
            err.println("Error: no spec-driven skills found in " + bundledSkillsDir);
            return 1;
        }
        int changed = 0;
        int skipped = 0;
        List<String> lines = new ArrayList<>();
        Path openspecDir = targetDir.resolve("openspec");
        Path specDir = targetDir.resolve(".spec-driven");
        try {
            if (Files.isDirectory(openspecDir)) {
                if (Files.exists(specDir)) {
                    lines.add("Skipped openspec/ rename: " + displayPath(specDir) + " already exists");
                    skipped++;
                } else {
                    Files.move(openspecDir, specDir);
                    lines.add("Moved openspec/ -> .spec-driven/");
                    changed++;
                }
            }
            if (Files.exists(specDir)) {
                changed += ensureSpecDrivenScaffold(specDir, lines);
            }
            for (MigrationTool tool : SUPPORTED_MIGRATION_TOOLS) {
                Path rootDir = targetDir.resolve(tool.rootDir);
                Path skillsDir = targetDir.resolve(tool.skillsDir);
                Path commandsDir = targetDir.resolve(tool.commandsDir);
                boolean hadOpenSpecSkills = hasMatchingEntries(skillsDir, SpecDrivenCliMain::isOpenSpecSkillName);
                boolean hadOpenSpecCommands = hasMatchingEntries(commandsDir, SpecDrivenCliMain::isOpenSpecCommandName);
                if (!Files.isDirectory(rootDir) || (!hadOpenSpecSkills && !hadOpenSpecCommands)) {
                    continue;
                }
                int removedSkills = removeMatchingEntries(skillsDir, SpecDrivenCliMain::isOpenSpecSkillName);
                int removedCommands = removeMatchingEntries(commandsDir, SpecDrivenCliMain::isOpenSpecCommandName);
                int installed = installBundledSkills(bundledSkillsDir, bundledSkills, skillsDir, targetDir);
                lines.add("Migrated " + tool.name + " tool config:");
                if (removedSkills > 0) {
                    lines.add("  removed " + removedSkills + " openspec skill artifact(s)");
                }
                if (removedCommands > 0) {
                    lines.add("  removed " + removedCommands + " openspec command artifact(s)");
                }
                if (installed > 0) {
                    lines.add("  installed " + installed + " auto-spec-driven skill(s)");
                }
                if (removedSkills == 0 && removedCommands == 0 && installed == 0) {
                    lines.add("  no changes needed");
                }
                changed += removedSkills + removedCommands + installed;
            }
            try (Stream<Path> stream = Files.list(targetDir)) {
                for (Path entry : stream.filter(Files::isDirectory).toList()) {
                    String name = entry.getFileName().toString();
                    if (!name.startsWith(".") || Set.of(".spec-driven", ".claude", ".opencode").contains(name)) {
                        continue;
                    }
                    if (!hasOpenSpecArtifacts(entry, 3)) {
                        continue;
                    }
                    lines.add("Skipped unsupported AI tool: " + name);
                    skipped++;
                }
            }
        } catch (IOException e) {
            err.println("Error: migrate failed: " + e.getMessage());
            return 1;
        }
        if (lines.isEmpty()) {
            lines.add("No OpenSpec artifacts found.");
        }
        lines.add("Done. " + changed + " change(s), " + skipped + " skipped.");
        out.println(String.join("\n", lines));
        return 0;
    }

    private int list() {
        Path changesDir = changesDir();
        if (!Files.isDirectory(changesDir)) {
            out.println("No .spec-driven/changes/ directory found.");
            return 0;
        }
        List<String> active = listDirectories(changesDir).stream().filter(name -> !"archive".equals(name)).toList();
        if (!active.isEmpty()) {
            out.println("Active:");
            for (String change : active) {
                out.println("  " + change + "    " + getStatus(change));
            }
        }
        Path archiveDir = changesDir.resolve("archive");
        if (Files.isDirectory(archiveDir)) {
            List<String> archived = listDirectories(archiveDir);
            if (!archived.isEmpty()) {
                out.println("Archived:");
                for (String archive : archived) {
                    out.println("  " + archive);
                }
            }
        }
        if (active.isEmpty() && (!Files.isDirectory(archiveDir) || listDirectories(archiveDir).isEmpty())) {
            out.println("No changes.");
        }
        return 0;
    }

    private String requireName(String command, List<String> args) {
        if (args.isEmpty()) {
            err.println("Usage: spec-driven " + command + " <change-name>");
            return null;
        }
        return args.get(0);
    }

    private Path requireChange(String name) {
        Path dir = changeDir(name);
        if (!Files.exists(dir)) {
            err.println("Error: change '" + name + "' not found at " + displayPath(dir));
            return null;
        }
        return dir;
    }

    private Path changesDir() {
        return workingDir.resolve(".spec-driven").resolve("changes");
    }

    private Path changeDir(String name) {
        return changesDir().resolve(name);
    }

    private Path resolveTargetDir(List<String> args) {
        if (args.isEmpty()) {
            return workingDir;
        }
        return Path.of(args.get(0)).toAbsolutePath().normalize();
    }

    private String getStatus(String name) {
        Path dir = changeDir(name);
        Path questionsPath = dir.resolve("questions.md");
        if (Files.exists(questionsPath)) {
            String content = readString(questionsPath);
            if (content.lines().anyMatch(line -> OPEN_QUESTION_PATTERN.matcher(line).find())) {
                return "blocked";
            }
        }
        Path tasksPath = dir.resolve("tasks.md");
        if (!Files.exists(tasksPath)) {
            return "proposed";
        }
        int total = 0;
        int complete = 0;
        for (String line : readString(tasksPath).split("\n")) {
            if (CHECKBOX_COMPLETE_PATTERN.matcher(line).find()) {
                total++;
                complete++;
            } else if (CHECKBOX_INCOMPLETE_PATTERN.matcher(line).find()) {
                total++;
            }
        }
        if (total == 0 || complete == 0) {
            return "proposed";
        }
        if (complete == total) {
            return "done";
        }
        return "in-progress (" + complete + "/" + total + ")";
    }

    private VerificationResult verifyChangeArtifactsDetailed(String name) {
        Path dir = changeDir(name);
        List<String> warnings = new ArrayList<>();
        List<String> errors = new ArrayList<>();
        if (!Files.exists(dir)) {
            errors.add("Change directory not found: " + displayPath(dir));
            return new VerificationResult(false, warnings, errors);
        }
        Path specsDir = dir.resolve("specs");
        if (!Files.exists(specsDir)) {
            errors.add("Missing required directory: specs/");
        } else {
            List<String> specFiles = findMdFiles(specsDir);
            if (specFiles.isEmpty()) {
                warnings.add("specs/ is empty — add delta files mirroring the main .spec-driven/specs/ structure");
            } else {
                Set<String> skipLines = Set.of(
                        "Leave a section empty if it does not apply.",
                        "Use RFC 2119 keywords: MUST (required), SHOULD (recommended), MAY (optional)."
                );
                for (String file : specFiles) {
                    String raw = readString(specsDir.resolve(file));
                    String stripped = raw.replaceAll("<!--[\\s\\S]*?-->", "");
                    boolean hasContent = stripped.lines().map(String::trim)
                            .anyMatch(line -> !line.isEmpty() && !line.startsWith("#") && !skipLines.contains(line));
                    if (!hasContent) {
                        warnings.add("specs/" + file + " has no content");
                    } else if (!Pattern.compile("^### Requirement:", Pattern.MULTILINE).matcher(stripped).find()) {
                        errors.add("specs/" + file + " has content but no '### Requirement:' headings — use the spec format");
                    } else if (!Pattern.compile("^## (ADDED|MODIFIED|REMOVED) Requirements$", Pattern.MULTILINE).matcher(stripped).find()) {
                        errors.add("specs/" + file + " is missing section marker — add '## ADDED Requirements', '## MODIFIED Requirements', or '## REMOVED Requirements' before each group of requirements");
                    }
                }
            }
        }
        for (String file : List.of("proposal.md", "design.md", "tasks.md", "questions.md")) {
            Path filePath = dir.resolve(file);
            if (!Files.exists(filePath)) {
                errors.add("Missing required artifact: " + file);
                continue;
            }
            String content = readString(filePath).trim();
            if (content.isEmpty()) {
                errors.add("Empty artifact: " + file);
                continue;
            }
            if (!"questions.md".equals(file) && (content.contains("[Describe") || content.contains("[List"))) {
                warnings.add(file + " contains unfilled placeholders");
            }
        }
        Path questionsPath = dir.resolve("questions.md");
        if (Files.exists(questionsPath) && readString(questionsPath).lines().anyMatch(line -> OPEN_QUESTION_PATTERN.matcher(line).find())) {
            errors.add("questions.md has open (unanswered) questions — resolve all questions before archiving");
        }
        Path tasksPath = dir.resolve("tasks.md");
        if (Files.exists(tasksPath)) {
            String tasksContent = readString(tasksPath);
            boolean hasTask = Pattern.compile("^\\s*-\\s*\\[[x ]\\]", Pattern.CASE_INSENSITIVE | Pattern.MULTILINE).matcher(tasksContent).find();
            if (!hasTask) {
                warnings.add("tasks.md has no checkboxes");
            } else if (CHECKBOX_INCOMPLETE_PATTERN.matcher(tasksContent).find()) {
                warnings.add("tasks.md has incomplete tasks");
            }
            errors.addAll(validateTestingTasks(tasksContent));
            if (!Pattern.compile("^## Testing", Pattern.MULTILINE).matcher(tasksContent).find()
                    && errors.stream().noneMatch(error -> error.contains("## Testing"))) {
                errors.add("tasks.md has no '## Testing' section — changes must include concrete test tasks");
            }
        }
        return new VerificationResult(errors.isEmpty(), warnings, errors);
    }

    private VerificationResult verifyChangeArtifactsBasic(String name) {
        Path dir = changeDir(name);
        List<String> warnings = new ArrayList<>();
        List<String> errors = new ArrayList<>();
        if (!Files.exists(dir)) {
            errors.add("Change directory not found: " + displayPath(dir));
            return new VerificationResult(false, warnings, errors);
        }
        Path specsDir = dir.resolve("specs");
        if (!Files.exists(specsDir)) {
            errors.add("Missing required directory: specs/");
        } else if (findMdFiles(specsDir).isEmpty()) {
            warnings.add("specs/ is empty — add delta files mirroring the main .spec-driven/specs/ structure");
        }
        for (String file : List.of("proposal.md", "design.md", "tasks.md", "questions.md")) {
            if (!Files.exists(dir.resolve(file))) {
                errors.add("Missing required artifact: " + file);
            }
        }
        Path tasksPath = dir.resolve("tasks.md");
        if (Files.exists(tasksPath) && CHECKBOX_INCOMPLETE_PATTERN.matcher(readString(tasksPath)).find()) {
            warnings.add("tasks.md has incomplete tasks");
        }
        return new VerificationResult(errors.isEmpty(), warnings, errors);
    }

    private List<String> validateTestingTasks(String tasksContent) {
        List<String> errors = new ArrayList<>();
        Map<String, List<String>> sections = readLevel2Sections(tasksContent);
        List<String> testingLines = sections.get("Testing");
        if (testingLines == null) {
            errors.add("tasks.md has no '## Testing' section — changes must include concrete test tasks");
            return errors;
        }
        List<String> testingTasks = testingLines.stream()
                .map(line -> {
                    Matcher matcher = Pattern.compile("^\\s*-\\s*\\[[x ]\\]\\s+(.+)$", Pattern.CASE_INSENSITIVE).matcher(line);
                    return matcher.find() ? matcher.group(1).trim() : "";
                })
                .filter(line -> !line.isEmpty())
                .toList();
        if (testingTasks.isEmpty()) {
            errors.add("tasks.md '## Testing' section has no checkbox tasks");
            return errors;
        }
        String lintTask = testingTasks.stream().filter(this::isLintOrValidationTask).findFirst().orElse(null);
        if (lintTask == null) {
            errors.add("tasks.md '## Testing' section must include at least one lint or validation task");
        } else if (!hasExplicitRunnableCommand(lintTask)) {
            errors.add("tasks.md lint or validation task must name an explicit runnable command");
        }
        String unitTask = testingTasks.stream().filter(this::isUnitTestTask).findFirst().orElse(null);
        if (unitTask == null) {
            errors.add("tasks.md '## Testing' section must include at least one unit test task");
        } else if (!hasExplicitRunnableCommand(unitTask)) {
            errors.add("tasks.md unit test task must name an explicit runnable command");
        }
        return errors;
    }

    private boolean isLintOrValidationTask(String task) {
        return Pattern.compile("\\b(lint|validate|validation|typecheck|type-check|build)\\b", Pattern.CASE_INSENSITIVE)
                .matcher(task).find();
    }

    private boolean isUnitTestTask(String task) {
        return Pattern.compile("\\b(unit test|unit tests)\\b", Pattern.CASE_INSENSITIVE).matcher(task).find();
    }

    private boolean hasExplicitRunnableCommand(String task) {
        if (Pattern.compile("`[^`]+`").matcher(task).find()) {
            return true;
        }
        return Pattern.compile("\\b(?:npm|pnpm|yarn|bun|node|bash|sh|pytest|jest|vitest|go|cargo|make|uv|poetry|mvnd|java)\\b",
                Pattern.CASE_INSENSITIVE).matcher(task).find();
    }

    private Map<String, List<String>> readLevel2Sections(String content) {
        Map<String, List<String>> sections = new LinkedHashMap<>();
        String current = null;
        for (String line : content.split("\n", -1)) {
            Matcher heading = Pattern.compile("^##\\s+(.+?)\\s*$").matcher(line);
            if (heading.find()) {
                current = heading.group(1).trim();
                sections.put(current, new ArrayList<>());
                continue;
            }
            if (current != null) {
                sections.get(current).add(line);
            }
        }
        return sections;
    }

    private int countBulletItems(List<String> lines) {
        if (lines == null) {
            return 0;
        }
        return (int) lines.stream().filter(line -> line.matches("^\\s*-\\s+.*$")).count();
    }

    private String firstNonEmptyLine(List<String> lines) {
        if (lines == null) {
            return "";
        }
        for (String line : lines) {
            String trimmed = line.trim();
            if (!trimmed.isEmpty()) {
                return trimmed;
            }
        }
        return "";
    }

    private List<String> readTopLevelBulletItems(List<String> lines) {
        if (lines == null) {
            return List.of();
        }
        return lines.stream()
                .map(line -> {
                    Matcher matcher = Pattern.compile("^\\s{0,3}-\\s+(.+)$").matcher(line);
                    return matcher.find() ? matcher.group(1).trim() : "";
                })
                .filter(item -> !item.isEmpty())
                .toList();
    }

    private ParsedStatus parseDeclaredRoadmapStatus(List<String> lines) {
        if (lines == null) {
            return new ParsedStatus(null, "missing status section");
        }
        List<String> nonEmpty = lines.stream().map(String::trim).filter(line -> !line.isEmpty()).toList();
        if (nonEmpty.size() != 1) {
            return new ParsedStatus(null, "Status section must contain exactly one bullet in the form '- Declared: <status>'");
        }
        Matcher match = Pattern.compile("^-\\s+Declared:\\s+([a-z-]+)$").matcher(nonEmpty.get(0));
        if (!match.find()) {
            return new ParsedStatus(null, "Status section must contain exactly one bullet in the form '- Declared: <status>'");
        }
        String status = match.group(1);
        if (!DECLARED_ROADMAP_STATUSES.contains(status)) {
            return new ParsedStatus(null, "unsupported declared roadmap status '" + status + "' (allowed: "
                    + String.join(", ", DECLARED_ROADMAP_STATUSES) + ")");
        }
        return new ParsedStatus(status, null);
    }

    private String readDeclaredRoadmapStatusLabel(List<String> lines) {
        ParsedStatus parsedStatus = parseDeclaredRoadmapStatus(lines);
        if (parsedStatus.declaredStatus != null) {
            return parsedStatus.declaredStatus;
        }
        if (lines == null) {
            return "invalid";
        }
        List<String> nonEmpty = lines.stream().map(String::trim).filter(line -> !line.isEmpty()).toList();
        if (nonEmpty.size() == 1) {
            Matcher rawMatch = Pattern.compile("^-\\s+Declared:\\s+(.+)$").matcher(nonEmpty.get(0));
            if (rawMatch.find()) {
                return rawMatch.group(1).trim();
            }
        }
        return "invalid";
    }

    private MilestoneIndexMetadata readMilestoneIndexMetadata(Path filePath) {
        String content = readString(filePath);
        Map<String, List<String>> sections = readLevel2Sections(content);
        return new MilestoneIndexMetadata(extractMarkdownTitle(content, stripMdExtension(filePath.getFileName().toString())),
                readDeclaredRoadmapStatusLabel(sections.get("Status")));
    }

    private void validateRoadmapIndex(Path roadmapDir, List<String> errors) {
        Path indexPath = roadmapDir.resolve("INDEX.md");
        if (!Files.exists(indexPath)) {
            errors.add("Missing roadmap index: .spec-driven/roadmap/INDEX.md");
            return;
        }
        List<String> lines = readString(indexPath).replace("\r\n", "\n").replace("\r", "\n").lines().toList();
        if (lines.isEmpty() || !lines.get(0).equals("# Roadmap Index")) {
            errors.add("roadmap/INDEX.md must start with '# Roadmap Index'");
        }
        List<String> milestoneHeadings = lines.stream().filter(line -> line.startsWith("## ")).toList();
        if (milestoneHeadings.size() != 1 || !"## Milestones".equals(milestoneHeadings.get(0))) {
            errors.add("roadmap/INDEX.md must contain exactly one '## Milestones' section");
            return;
        }
        int milestoneHeadingIndex = lines.indexOf("## Milestones");
        for (String line : lines.subList(1, milestoneHeadingIndex)) {
            if (!line.trim().isEmpty()) {
                errors.add("roadmap/INDEX.md may only contain blank lines before '## Milestones'");
                break;
            }
        }
        Set<String> seenEntries = new LinkedHashSet<>();
        Pattern pattern = Pattern.compile("^- \\[([^\\]]+)\\]\\(milestones/([^)]+)\\) - (.+) - (proposed|active|blocked|complete)$");
        for (String line : lines.subList(milestoneHeadingIndex + 1, lines.size())) {
            String trimmed = line.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            Matcher matcher = pattern.matcher(trimmed);
            if (!matcher.find()) {
                errors.add("roadmap/INDEX.md entries must match '- [<file>](milestones/<file>) - <title> - <declared-status>' where <declared-status> is one of: "
                        + String.join(", ", DECLARED_ROADMAP_STATUSES));
                continue;
            }
            String label = matcher.group(1);
            String relativePath = matcher.group(2);
            if (!label.equals(Path.of(relativePath).getFileName().toString())) {
                errors.add("roadmap/INDEX.md entry label '" + label + "' must match '" + Path.of(relativePath).getFileName() + "'");
            }
            Path milestonePath = roadmapDir.resolve("milestones").resolve(relativePath);
            if (!Files.exists(milestonePath)) {
                errors.add("roadmap/INDEX.md entry references missing milestone '" + relativePath + "'");
                continue;
            }
            String milestoneDeclaredStatus = readMilestoneIndexMetadata(milestonePath).declaredStatus;
            String indexDeclaredStatus = matcher.group(4);
            if (!indexDeclaredStatus.equals(milestoneDeclaredStatus)) {
                errors.add("roadmap/INDEX.md entry status '" + indexDeclaredStatus + "' must match milestone declared status '"
                        + milestoneDeclaredStatus + "' for '" + relativePath + "'");
            }
            if (!seenEntries.add(relativePath)) {
                errors.add("roadmap/INDEX.md contains duplicate entry for '" + relativePath + "'");
            }
        }
    }

    private List<String> readPlannedChangeStates(Path specDir, List<String> plannedChangeNames) {
        Set<String> activeChanges = new LinkedHashSet<>();
        Set<String> archivedChanges = new LinkedHashSet<>();
        Path targetChangesDir = specDir.resolve("changes");
        if (Files.isDirectory(targetChangesDir)) {
            activeChanges.addAll(listDirectories(targetChangesDir).stream().filter(name -> !"archive".equals(name)).toList());
        }
        Path archiveDir = targetChangesDir.resolve("archive");
        if (Files.isDirectory(archiveDir)) {
            for (String entry : listDirectories(archiveDir)) {
                Matcher match = Pattern.compile("^\\d{4}-\\d{2}-\\d{2}-(.+)$").matcher(entry);
                archivedChanges.add(match.find() ? match.group(1) : entry);
            }
        }
        return plannedChangeNames.stream().map(name -> {
            if (archivedChanges.contains(name)) {
                return "archived";
            }
            if (activeChanges.contains(name)) {
                return "active";
            }
            return "missing";
        }).toList();
    }

    private String deriveMilestoneStatus(List<String> plannedChangeStates) {
        if (plannedChangeStates.isEmpty()) {
            return "proposed";
        }
        if (plannedChangeStates.stream().allMatch("archived"::equals)) {
            return "complete";
        }
        if (plannedChangeStates.stream().anyMatch("active"::equals)) {
            return "active";
        }
        return "proposed";
    }

    private String derivePlannedChangeDeclaredStatus(String state) {
        return "archived".equals(state) ? "complete" : "planned";
    }

    private List<PlannedChangeEntry> readPlannedChangeEntries(List<String> lines) {
        if (lines == null) {
            return List.of();
        }
        return lines.stream()
                .map(this::parsePlannedChangeEntry)
                .filter(Objects::nonNull)
                .toList();
    }

    private PlannedChangeEntry parsePlannedChangeEntry(String line) {
        PlannedChangeEntry raw = parseRawPlannedChangeEntry(line);
        if (raw == null || !DECLARED_PLANNED_CHANGE_STATUSES.contains(raw.declaredStatus)) {
            return null;
        }
        return raw;
    }

    private PlannedChangeEntry parseRawPlannedChangeEntry(String line) {
        Matcher match = Pattern.compile("^-\\s+`([a-z0-9]+(?:-[a-z0-9]+)*)`\\s+-\\s+Declared:\\s+([a-z-]+)\\s+-\\s+(.+)$")
                .matcher(line.trim());
        if (!match.find()) {
            return null;
        }
        String summary = match.group(3).trim();
        if (summary.isEmpty()) {
            return null;
        }
        return new PlannedChangeEntry(match.group(1), match.group(2), summary);
    }

    private String validatePlannedChangeLines(List<String> lines) {
        if (lines == null) {
            return null;
        }
        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            if (line.matches("^\\s{0,3}-\\s+.*$")) {
                PlannedChangeEntry raw = parseRawPlannedChangeEntry(line);
                if (raw == null) {
                    return "expected '- `\\<change-name>\\` - Declared: <planned|complete> - <summary>'";
                }
                if (!DECLARED_PLANNED_CHANGE_STATUSES.contains(raw.declaredStatus)) {
                    return "unsupported planned change declared status '" + raw.declaredStatus + "' (allowed: "
                            + String.join(", ", DECLARED_PLANNED_CHANGE_STATUSES) + ")";
                }
                continue;
            }
            return "planned change descriptions must remain on a single line";
        }
        return null;
    }

    private void reconcileRoadmapAfterArchive(Path targetDir, String name) throws IOException {
        Path specDir = targetDir.resolve(".spec-driven");
        Path roadmapDir = specDir.resolve("roadmap");
        Path milestonesDir = roadmapDir.resolve("milestones");
        if (!Files.isDirectory(roadmapDir) || !Files.isDirectory(milestonesDir)) {
            return;
        }
        for (String file : findMdFiles(milestonesDir)) {
            Path filePath = milestonesDir.resolve(file);
            String content = readString(filePath);
            Map<String, List<String>> sections = readLevel2Sections(content);
            String plannedChangeError = validatePlannedChangeLines(sections.get("Planned Changes"));
            if (plannedChangeError != null) {
                continue;
            }
            List<PlannedChangeEntry> entries = readPlannedChangeEntries(sections.get("Planned Changes"));
            List<String> names = entries.stream().map(entry -> entry.name).toList();
            if (!names.contains(name) || !sections.containsKey("Status")) {
                continue;
            }
            List<String> states = readPlannedChangeStates(specDir, names);
            String derivedStatus = deriveMilestoneStatus(states);
            List<PlannedChangeEntry> reconciledEntries = new ArrayList<>();
            for (int i = 0; i < entries.size(); i++) {
                PlannedChangeEntry entry = entries.get(i);
                reconciledEntries.add(new PlannedChangeEntry(entry.name, derivePlannedChangeDeclaredStatus(states.get(i)), entry.summary));
            }
            ParsedStatus parsedStatus = parseDeclaredRoadmapStatus(sections.get("Status"));
            String nextContent = content;
            boolean changedEntries = false;
            for (int i = 0; i < entries.size(); i++) {
                if (!entries.get(i).declaredStatus.equals(reconciledEntries.get(i).declaredStatus)) {
                    changedEntries = true;
                    break;
                }
            }
            if (changedEntries) {
                nextContent = replacePlannedChangesSection(nextContent, reconciledEntries);
            }
            if (parsedStatus.declaredStatus == null || !parsedStatus.declaredStatus.equals(derivedStatus)) {
                nextContent = replaceMilestoneDeclaredStatus(nextContent, derivedStatus);
            }
            if (!nextContent.equals(content)) {
                Files.writeString(filePath, nextContent, StandardCharsets.UTF_8);
            }
        }
        Files.writeString(roadmapDir.resolve("INDEX.md"), buildRoadmapIndexContent(roadmapDir), StandardCharsets.UTF_8);
    }

    private String replaceMilestoneDeclaredStatus(String content, String declaredStatus) {
        return rewriteLevel2Section(content, "Status", List.of("- Declared: " + declaredStatus, ""));
    }

    private String replacePlannedChangesSection(String content, List<PlannedChangeEntry> entries) {
        List<String> bodyLines = new ArrayList<>(entries.stream().map(this::formatPlannedChangeEntry).toList());
        bodyLines.add("");
        return rewriteLevel2Section(content, "Planned Changes", bodyLines);
    }

    private String formatPlannedChangeEntry(PlannedChangeEntry entry) {
        return "- `" + entry.name + "` - Declared: " + entry.declaredStatus + " - " + entry.summary;
    }

    private String rewriteLevel2Section(String content, String heading, List<String> bodyLines) {
        String normalized = content.replace("\r\n", "\n").replace("\r", "\n");
        List<String> lines = Arrays.asList(normalized.split("\n", -1));
        List<String> rewritten = new ArrayList<>();
        boolean seen = false;
        for (int index = 0; index < lines.size();) {
            Matcher match = Pattern.compile("^##\\s+(.+?)\\s*$").matcher(lines.get(index));
            if (!match.find() || !match.group(1).trim().equals(heading)) {
                rewritten.add(lines.get(index));
                index += 1;
                continue;
            }
            int nextIndex = index + 1;
            while (nextIndex < lines.size() && !lines.get(nextIndex).matches("^##\\s+.*$")) {
                nextIndex += 1;
            }
            if (!seen) {
                rewritten.add("## " + heading);
                rewritten.addAll(bodyLines);
                seen = true;
            }
            index = nextIndex;
        }
        String nextContent = String.join("\n", rewritten);
        return normalized.endsWith("\n") ? nextContent + "\n" : nextContent;
    }

    private MaintenanceConfig loadMaintenanceConfig(Path targetDir) {
        Path configPath = targetDir.resolve(".spec-driven").resolve("maintenance").resolve("config.json");
        if (!Files.exists(configPath)) {
            printJsonTo(err, mapOf("status", "error", "message", "maintenance config not found: " + displayPath(configPath),
                    "hint", "Create .spec-driven/maintenance/config.json with explicit checks before running maintenance."));
            return null;
        }
        Map<String, Object> parsed;
        try {
            parsed = org.specdriven.agent.json.JsonReader.parseObject(readString(configPath));
        } catch (RuntimeException e) {
            printJsonTo(err, mapOf("status", "error", "message", "invalid maintenance config: " + displayPath(configPath)));
            return null;
        }
        String changePrefix = stringOrDefault(parsed.get("changePrefix"), DEFAULT_MAINTENANCE_CHANGE_PREFIX);
        String branchPrefix = stringOrDefault(parsed.get("branchPrefix"), DEFAULT_MAINTENANCE_BRANCH_PREFIX);
        String commitMessagePrefix = stringOrDefault(parsed.get("commitMessagePrefix"), DEFAULT_MAINTENANCE_COMMIT_PREFIX);
        List<MaintenanceCheck> checks = new ArrayList<>();
        Object rawChecks = parsed.get("checks");
        if (rawChecks instanceof List<?> list) {
            for (Object item : list) {
                if (item instanceof Map<?, ?> map) {
                    String name = map.get("name") instanceof String s ? s : null;
                    String command = map.get("command") instanceof String s ? s : null;
                    String fixCommand = map.get("fixCommand") instanceof String s ? s : null;
                    if (name != null && command != null) {
                        checks.add(new MaintenanceCheck(name, command, fixCommand));
                    }
                }
            }
        }
        return new MaintenanceConfig(changePrefix, branchPrefix, commitMessagePrefix, checks);
    }

    private String stringOrDefault(Object value, String defaultValue) {
        if (value instanceof String s && !s.trim().isEmpty()) {
            return s;
        }
        return defaultValue;
    }

    private ShellCommandResult runShellCommand(String commandText, Path cwd) {
        ProcessBuilder builder = new ProcessBuilder("/bin/sh", "-lc", commandText);
        builder.directory(cwd.toFile());
        try {
            Process process = builder.start();
            byte[] stdoutBytes = process.getInputStream().readAllBytes();
            byte[] stderrBytes = process.getErrorStream().readAllBytes();
            int status = process.waitFor();
            return new ShellCommandResult(status, new String(stdoutBytes, StandardCharsets.UTF_8),
                    new String(stderrBytes, StandardCharsets.UTF_8));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return new ShellCommandResult(1, "", e.getMessage() == null ? "" : e.getMessage());
        } catch (IOException e) {
            return new ShellCommandResult(1, "", e.getMessage() == null ? "" : e.getMessage());
        }
    }

    private String findActiveMaintenanceChange(Path targetDir, String changePrefix) {
        Path targetChangesDir = targetDir.resolve(".spec-driven").resolve("changes");
        if (!Files.isDirectory(targetChangesDir)) {
            return null;
        }
        return listDirectories(targetChangesDir).stream()
                .filter(name -> !"archive".equals(name))
                .filter(name -> name.startsWith(changePrefix))
                .findFirst()
                .orElse(null);
    }

    private String makeMaintenanceStamp() {
        String iso = OffsetDateTime.now(ZoneOffset.UTC).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME).replace("-", "").replace(":", "");
        return iso.substring(0, 8) + "-" + iso.substring(9, 15);
    }

    private void seedMaintenanceChange(Path targetDir, String name, String branchName, List<MaintenanceCheck> failingChecks,
                                       MaintenanceConfig config) throws IOException {
        Path dir = targetDir.resolve(".spec-driven").resolve("changes").resolve(name);
        Files.createDirectories(dir.resolve("specs"));
        String checkList = failingChecks.stream().map(check -> "`" + check.name + "`").collect(Collectors.joining(", "));
        Files.writeString(dir.resolve("proposal.md"), String.join("\n",
                "# " + name,
                "",
                "## What",
                "",
                "Apply the configured maintenance auto-fixes for the failing checks " + checkList + " on branch `" + branchName + "`.",
                "",
                "## Why",
                "",
                "The manual maintenance workflow detected checks that are explicitly configured as safe to auto-fix.",
                "",
                "## Scope",
                "",
                "- Apply only the configured maintenance fix commands for the currently failing checks",
                "- Re-run the configured maintenance checks",
                "- Archive the completed maintenance change automatically on success",
                "",
                "## Unchanged Behavior",
                "",
                "- Do not modify unrelated active changes",
                "- Do not guess at failures that are not explicitly configured as safe to auto-fix",
                ""), StandardCharsets.UTF_8);
        Files.writeString(dir.resolve("design.md"), String.join("\n",
                "# Design: " + name,
                "",
                "## Approach",
                "",
                "Run the configured auto-fix commands for the failing checks, then re-run the configured maintenance checks and archive the change if they pass.",
                "",
                "## Key Decisions",
                "",
                "- Use the configured branch prefix `" + config.branchPrefix + "` and change prefix `" + config.changePrefix + "`",
                "- Limit fixes to the configured failing checks: " + checkList,
                "",
                "## Alternatives Considered",
                "",
                "- Skip the failing checks entirely",
                "- Attempt speculative repairs beyond the configured maintenance commands",
                ""), StandardCharsets.UTF_8);
        Files.writeString(dir.resolve("tasks.md"), String.join("\n",
                "# Tasks: " + name,
                "",
                "## Implementation",
                "",
                "- [ ] Apply configured auto-fixes for the failing maintenance checks",
                "",
                "## Testing",
                "",
                "- [ ] Re-run the configured maintenance checks and confirm they pass",
                "",
                "## Verification",
                "",
                "- [ ] Verify the maintenance change is valid and archive it",
                ""), StandardCharsets.UTF_8);
        Files.writeString(dir.resolve("questions.md"), String.join("\n",
                "# Questions: " + name,
                "",
                "## Open",
                "",
                "<!-- No open questions -->",
                "",
                "## Resolved",
                "",
                "<!-- Scheduled maintenance change generated without open questions -->",
                ""), StandardCharsets.UTF_8);
    }

    private void markTaskComplete(Path tasksPath, String taskText) {
        String content = readString(tasksPath);
        String escaped = Pattern.quote(taskText);
        Pattern pattern = Pattern.compile("^- \\[ \\] " + escaped + "$", Pattern.MULTILINE);
        Matcher matcher = pattern.matcher(content);
        if (!matcher.find()) {
            return;
        }
        String updated = matcher.replaceFirst("- [x] " + Matcher.quoteReplacement(taskText));
        writeString(tasksPath, updated);
    }

    private ArchiveResult tryArchiveChange(String name) {
        Path src = changeDir(name);
        if (!Files.exists(src)) {
            return new ArchiveResult(false, null, "Change directory not found: " + displayPath(src));
        }
        Path archivePath = changesDir().resolve("archive").resolve(formatLocalDate() + "-" + name);
        if (Files.exists(archivePath)) {
            return new ArchiveResult(false, null, "archive target already exists: " + displayPath(archivePath));
        }
        try {
            Files.createDirectories(archivePath.getParent());
            Files.move(src, archivePath);
            reconcileRoadmapAfterArchive(workingDir, name);
            return new ArchiveResult(true, displayPath(archivePath), null);
        } catch (IOException e) {
            return new ArchiveResult(false, null, e.getMessage());
        }
    }

    private String shellQuote(String value) {
        return "'" + value.replace("'", "'\\''") + "'";
    }

    private int ensureSpecDrivenScaffold(Path specDir, List<String> lines) throws IOException {
        int changed = 0;
        Path changesPath = specDir.resolve("changes");
        Path specsPath = specDir.resolve("specs");
        Path roadmapPath = specDir.resolve("roadmap");
        Path roadmapMilestonesPath = roadmapPath.resolve("milestones");
        if (!Files.exists(changesPath)) {
            Files.createDirectories(changesPath);
            lines.add("Created .spec-driven/changes/");
            changed++;
        }
        if (!Files.exists(specsPath)) {
            Files.createDirectories(specsPath);
            lines.add("Created .spec-driven/specs/");
            changed++;
        }
        if (!Files.exists(roadmapPath)) {
            Files.createDirectories(roadmapPath);
            lines.add("Created .spec-driven/roadmap/");
            changed++;
        }
        if (!Files.exists(roadmapMilestonesPath)) {
            Files.createDirectories(roadmapMilestonesPath);
            lines.add("Created .spec-driven/roadmap/milestones/");
            changed++;
        }
        if (!Files.exists(specDir.resolve("config.yaml"))) {
            Files.writeString(specDir.resolve("config.yaml"), INIT_CONFIG_YAML, StandardCharsets.UTF_8);
            lines.add("Created .spec-driven/config.yaml");
            changed++;
        }
        if (!Files.exists(specsPath.resolve("INDEX.md"))) {
            Files.writeString(specsPath.resolve("INDEX.md"), INIT_INDEX_MD, StandardCharsets.UTF_8);
            lines.add("Created .spec-driven/specs/INDEX.md");
            changed++;
        }
        if (!Files.exists(specsPath.resolve("README.md"))) {
            Files.writeString(specsPath.resolve("README.md"), INIT_README_MD, StandardCharsets.UTF_8);
            lines.add("Created .spec-driven/specs/README.md");
            changed++;
        }
        if (!Files.exists(roadmapPath.resolve("INDEX.md"))) {
            Files.writeString(roadmapPath.resolve("INDEX.md"), INIT_ROADMAP_INDEX_MD, StandardCharsets.UTF_8);
            lines.add("Created .spec-driven/roadmap/INDEX.md");
            changed++;
        }
        return changed;
    }

    private void regenerateIndexMd(Path specsDir, List<String> lines) throws IOException {
        if (!Files.exists(specsDir)) {
            return;
        }
        Path indexPath = specsDir.resolve("INDEX.md");
        List<String> mdFiles = findMdFiles(specsDir).stream()
                .filter(file -> !Set.of("INDEX.md", "README.md").contains(file))
                .filter(file -> !Set.of("INDEX.md", "README.md").contains(Path.of(file).getFileName().toString()))
                .toList();
        Files.writeString(indexPath, buildSpecsIndexContent(specsDir), StandardCharsets.UTF_8);
        lines.add("Regenerated specs/INDEX.md (" + mdFiles.size() + " file(s) listed)");
    }

    private String buildSpecsIndexContent(Path specsDir) {
        Set<String> excluded = Set.of("INDEX.md", "README.md");
        List<String> mdFiles = findMdFiles(specsDir).stream()
                .filter(file -> !excluded.contains(file))
                .filter(file -> !excluded.contains(Path.of(file).getFileName().toString()))
                .toList();
        if (mdFiles.isEmpty()) {
            return INIT_INDEX_MD;
        }
        Map<String, List<String>> grouped = new LinkedHashMap<>();
        for (String file : mdFiles) {
            String category = normalizePathForMarkdown(Path.of(file).getParent() == null ? "." : Path.of(file).getParent().toString());
            String key = ".".equals(category) ? "root" : category;
            grouped.computeIfAbsent(key, unused -> new ArrayList<>()).add(file);
        }
        List<String> lines = new ArrayList<>(List.of("# Specs Index", ""));
        List<String> categories = new ArrayList<>(grouped.keySet());
        categories.sort(Comparator.naturalOrder());
        for (String category : categories) {
            lines.add("## " + category);
            List<String> files = new ArrayList<>(grouped.get(category));
            files.sort(Comparator.naturalOrder());
            for (String file : files) {
                String relativePath = normalizePathForMarkdown(file);
                String summary = readSpecSummary(specsDir, file);
                lines.add("- [" + Path.of(file).getFileName() + "](" + relativePath + ") - " + summary);
            }
            lines.add("");
        }
        return String.join("\n", lines).stripTrailing() + "\n";
    }

    private String readSpecSummary(Path specsDir, String relativePath) {
        Path filePath = specsDir.resolve(relativePath);
        String fallback = stripMdExtension(Path.of(relativePath).getFileName().toString());
        return extractMarkdownTitle(readString(filePath), fallback);
    }

    private String extractMarkdownTitle(String content, String fallback) {
        Matcher title = Pattern.compile("^#\\s+(.+?)\\s*$", Pattern.MULTILINE).matcher(content);
        if (title.find()) {
            String value = title.group(1).trim();
            if (!value.isEmpty()) {
                return value;
            }
        }
        return fallback;
    }

    private void regenerateRoadmapIndex(Path roadmapDir, List<String> lines) throws IOException {
        if (!Files.exists(roadmapDir)) {
            return;
        }
        Files.writeString(roadmapDir.resolve("INDEX.md"), buildRoadmapIndexContent(roadmapDir), StandardCharsets.UTF_8);
        lines.add("Regenerated roadmap/INDEX.md");
    }

    private String buildRoadmapIndexContent(Path roadmapDir) {
        Path milestonesDir = roadmapDir.resolve("milestones");
        List<String> milestoneFiles = findMdFiles(milestonesDir);
        List<String> lines = new ArrayList<>(List.of("# Roadmap Index", "", "## Milestones"));
        for (String file : milestoneFiles) {
            String relativePath = normalizePathForMarkdown("milestones/" + file);
            MilestoneIndexMetadata metadata = readMilestoneIndexMetadata(milestonesDir.resolve(file));
            lines.add("- [" + Path.of(file).getFileName() + "](" + relativePath + ") - " + metadata.title + " - " + metadata.declaredStatus);
        }
        return String.join("\n", lines) + "\n";
    }

    private Path resolveBundledSkillsDir() {
        String configured = System.getProperty("specdriven.skills.dir");
        if (configured == null || configured.isBlank()) {
            configured = System.getenv("SPECDRIVEN_SKILLS_DIR");
        }
        if (configured != null && !configured.isBlank()) {
            Path path = Path.of(configured).toAbsolutePath().normalize();
            if (Files.isDirectory(path)) {
                return path;
            }
        }
        Path codeSource = resolveCodeSourcePath();
        List<Path> candidates = new ArrayList<>();
        if (codeSource != null) {
            Path base = Files.isRegularFile(codeSource) ? codeSource.getParent() : codeSource;
            candidates.add(base.resolveSibling("skills"));
            candidates.add(base.resolve("skills"));
            if (base.getParent() != null) {
                candidates.add(base.getParent().resolve("skills"));
                candidates.add(base.getParent());
            }
        }
        for (Path candidate : candidates) {
            if (!Files.isDirectory(candidate)) {
                continue;
            }
            try (Stream<Path> stream = Files.list(candidate)) {
                boolean found = stream.anyMatch(path -> Files.isDirectory(path)
                        && path.getFileName().toString().startsWith("spec-driven-")
                        && Files.exists(path.resolve("SKILL.md")));
                if (found) {
                    return candidate;
                }
            } catch (IOException ignored) {
            }
        }
        return null;
    }

    private Path resolveCodeSourcePath() {
        try {
            if (SpecDrivenCliMain.class.getProtectionDomain().getCodeSource() == null
                    || SpecDrivenCliMain.class.getProtectionDomain().getCodeSource().getLocation() == null) {
                return null;
            }
            return Path.of(SpecDrivenCliMain.class.getProtectionDomain().getCodeSource().getLocation().toURI())
                    .toAbsolutePath().normalize();
        } catch (URISyntaxException | IllegalArgumentException e) {
            return null;
        }
    }

    private int installBundledSkills(Path sourceDir, List<String> skills, Path targetDir, Path projectDir) throws IOException {
        int installed = 0;
        if (!Files.exists(targetDir)) {
            Files.createDirectories(targetDir);
        }
        for (String skill : skills) {
            Path src = sourceDir.resolve(skill);
            Path dest = targetDir.resolve(skill);
            if (Files.exists(dest)) {
                continue;
            }
            Files.createDirectories(dest.resolve("scripts"));
            String skillDirRef = normalizePathForMarkdown(projectDir.relativize(dest).toString());
            if (skillDirRef.isBlank()) {
                skillDirRef = ".";
            }
            String scriptPrefix = skillDirRef + "/scripts/spec-driven";
            String skillContent = readString(src.resolve("SKILL.md"))
                    .replace("{{SKILL_DIR}}", skillDirRef)
                    .replace("node " + skillDirRef + "/scripts/spec-driven.js", scriptPrefix);
            Files.writeString(dest.resolve("SKILL.md"), skillContent, StandardCharsets.UTF_8);
            Files.writeString(dest.resolve("scripts").resolve("spec-driven"), bundledWrapperScript(), StandardCharsets.UTF_8);
            dest.resolve("scripts").resolve("spec-driven").toFile().setExecutable(true, false);
            installed++;
        }
        return installed;
    }

    private String bundledWrapperScript() {
        return """
                #!/bin/sh
                set -eu

                if [ -n "${SPEC_DRIVEN_CLI_CLASSPATH:-}" ]; then
                  exec java -cp "$SPEC_DRIVEN_CLI_CLASSPATH" org.specdriven.cli.SpecDrivenCliMain "$@"
                fi

                if [ -n "${SPEC_DRIVEN_CLI_CMD:-}" ]; then
                  exec /bin/sh -lc "${SPEC_DRIVEN_CLI_CMD} \"\\$@\"" sh "$@"
                fi

                if command -v spec-driven >/dev/null 2>&1; then
                  exec spec-driven "$@"
                fi

                echo "spec-driven CLI not found. Set SPEC_DRIVEN_CLI_CLASSPATH or SPEC_DRIVEN_CLI_CMD, or install 'spec-driven' on PATH." >&2
                exit 1
                """;
    }

    private boolean hasMatchingEntries(Path dir, NameMatcher matcher) throws IOException {
        if (!Files.isDirectory(dir)) {
            return false;
        }
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir)) {
            for (Path entry : stream) {
                if (matcher.matches(entry.getFileName().toString())) {
                    return true;
                }
            }
        }
        return false;
    }

    private int removeMatchingEntries(Path dir, NameMatcher matcher) throws IOException {
        if (!Files.isDirectory(dir)) {
            return 0;
        }
        int removed = 0;
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir)) {
            for (Path entry : stream) {
                if (!matcher.matches(entry.getFileName().toString())) {
                    continue;
                }
                deleteRecursively(entry);
                removed++;
            }
        }
        return removed;
    }

    private boolean hasOpenSpecArtifacts(Path dir, int depth) throws IOException {
        if (!Files.isDirectory(dir)) {
            return false;
        }
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir)) {
            for (Path entry : stream) {
                String name = entry.getFileName().toString();
                if (isOpenSpecSkillName(name) || isOpenSpecCommandName(name)) {
                    return true;
                }
                if (depth > 0 && Files.isDirectory(entry) && hasOpenSpecArtifacts(entry, depth - 1)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean isOpenSpecSkillName(String name) {
        return name.startsWith("openspec-");
    }

    private static boolean isOpenSpecCommandName(String name) {
        return name.equals("opsx")
                || name.equals("openspec")
                || name.startsWith("opsx-")
                || name.startsWith("openspec-")
                || name.startsWith("opsx:")
                || name.startsWith("openspec:");
    }

    private List<String> findMdFiles(Path dir) {
        if (!Files.exists(dir)) {
            return List.of();
        }
        List<String> files = new ArrayList<>();
        collectMdFiles(dir, dir, files);
        files.sort(Comparator.naturalOrder());
        return files;
    }

    private void collectMdFiles(Path root, Path dir, List<String> files) {
        if (!Files.exists(dir)) {
            return;
        }
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir)) {
            for (Path entry : stream) {
                if (Files.isDirectory(entry)) {
                    collectMdFiles(root, entry, files);
                } else if (entry.getFileName().toString().endsWith(".md")) {
                    files.add(normalizePathForMarkdown(root.relativize(entry).toString()));
                }
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to read markdown files from " + dir, e);
        }
    }

    private List<String> listDirectories(Path dir) {
        if (!Files.isDirectory(dir)) {
            return List.of();
        }
        try (Stream<Path> stream = Files.list(dir)) {
            return stream.filter(Files::isDirectory)
                    .map(path -> path.getFileName().toString())
                    .sorted()
                    .toList();
        } catch (IOException e) {
            return List.of();
        }
    }

    private String normalizePathForMarkdown(String value) {
        return value.replace('\\', '/');
    }

    private String displayPath(Path path) {
        return normalizePathForMarkdown(workingDir.relativize(path.toAbsolutePath().normalize()).toString());
    }

    private String formatLocalDate() {
        return LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE);
    }

    private String stripMdExtension(String fileName) {
        return fileName.endsWith(".md") ? fileName.substring(0, fileName.length() - 3) : fileName;
    }

    private String readString(Path path) {
        try {
            return Files.readString(path, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new RuntimeException("Failed to read " + path + ": " + e.getMessage(), e);
        }
    }

    private void writeString(Path path, String content) {
        try {
            Files.writeString(path, content, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new RuntimeException("Failed to write " + path + ": " + e.getMessage(), e);
        }
    }

    private void deleteRecursively(Path path) throws IOException {
        if (!Files.exists(path)) {
            return;
        }
        try (Stream<Path> stream = Files.walk(path)) {
            for (Path entry : stream.sorted(Comparator.reverseOrder()).toList()) {
                Files.deleteIfExists(entry);
            }
        }
    }

    private void printJson(Map<String, Object> value) {
        out.println(JsonWriter.fromMap(value));
    }

    private void printJsonTo(PrintStream stream, Map<String, Object> value) {
        stream.println(JsonWriter.fromMap(value));
    }

    private Map<String, Object> mapOf(Object... keyValues) {
        Map<String, Object> map = new LinkedHashMap<>();
        for (int i = 0; i < keyValues.length; i += 2) {
            map.put((String) keyValues[i], keyValues[i + 1]);
        }
        return map;
    }

    @FunctionalInterface
    private interface NameMatcher {
        boolean matches(String value);
    }

    private record ParsedStatus(String declaredStatus, String error) {
    }

    private record PlannedChangeEntry(String name, String declaredStatus, String summary) {
    }

    private record MilestoneIndexMetadata(String title, String declaredStatus) {
    }

    private record VerificationResult(boolean valid, List<String> warnings, List<String> errors) {
        Map<String, Object> toMap() {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("valid", valid);
            map.put("warnings", warnings);
            map.put("errors", errors);
            return map;
        }
    }

    private record MigrationTool(String name, String rootDir, String skillsDir, String commandsDir) {
    }

    private record MaintenanceCheck(String name, String command, String fixCommand) {
    }

    private record MaintenanceConfig(String changePrefix, String branchPrefix, String commitMessagePrefix,
                                     List<MaintenanceCheck> checks) {
    }

    private record ShellCommandResult(int status, String stdout, String stderr) {
    }

    private record ServiceRuntimeArgs(
            Path servicesSql,
            String host,
            int port,
            String jdbcUrl,
            Path compileCachePath,
            Set<String> apiKeys,
            boolean exitAfterStart) {

        ServiceRuntimeLauncher.Options toOptions() {
            return new ServiceRuntimeLauncher.Options(
                    servicesSql,
                    host,
                    port,
                    jdbcUrl,
                    compileCachePath,
                    apiKeys);
        }
    }

    private record CheckResult(MaintenanceCheck check, ShellCommandResult result) {
    }

    private record ArchiveResult(boolean ok, String archivePath, String error) {
    }

    static Result runForTest(Path workingDir, String... args) {
        ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        ByteArrayOutputStream stderr = new ByteArrayOutputStream();
        int exitCode = run(args, workingDir,
                new PrintStream(stdout, true, StandardCharsets.UTF_8),
                new PrintStream(stderr, true, StandardCharsets.UTF_8));
        return new Result(exitCode, stdout.toString(StandardCharsets.UTF_8), stderr.toString(StandardCharsets.UTF_8));
    }

    record Result(int exitCode, String stdout, String stderr) {
    }
}
