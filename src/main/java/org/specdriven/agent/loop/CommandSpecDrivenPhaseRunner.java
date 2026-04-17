package org.specdriven.agent.loop;

import org.specdriven.agent.tool.ProfileBoundCommandExecutor;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

/**
 * Runs spec-driven pipeline phases by launching configured workflow commands.
 */
public final class CommandSpecDrivenPhaseRunner implements SpecDrivenPhaseRunner {

    private static final Map<PipelinePhase, String> DEFAULT_SUBCOMMANDS = Map.of(
            PipelinePhase.PROPOSE, "propose",
            PipelinePhase.IMPLEMENT, "apply",
            PipelinePhase.VERIFY, "verify",
            PipelinePhase.REVIEW, "review",
            PipelinePhase.ARCHIVE, "archive"
    );

    private final Map<PipelinePhase, List<String>> commandTemplates;
    private final ProfileBoundCommandExecutor profileExecutor;

    /**
     * Creates a command runner using `spec-driven <subcommand> <change-name>`.
     */
    public CommandSpecDrivenPhaseRunner() {
        this(List.of("spec-driven"));
    }

    /**
     * Creates a command runner using the supplied command prefix followed by the
     * default phase subcommand and change name.
     */
    public CommandSpecDrivenPhaseRunner(List<String> commandPrefix) {
        this(defaultCommandTemplates(commandPrefix));
    }

    /**
     * Creates a command runner with explicit command templates per phase.
     * Empty templates are treated as no-op success, which is useful for RECOMMEND.
     */
    public CommandSpecDrivenPhaseRunner(Map<PipelinePhase, List<String>> commandTemplates) {
        this(commandTemplates, ProfileBoundCommandExecutor.DEFAULT);
    }

    CommandSpecDrivenPhaseRunner(Map<PipelinePhase, List<String>> commandTemplates,
                                 ProfileBoundCommandExecutor profileExecutor) {
        Objects.requireNonNull(commandTemplates, "commandTemplates must not be null");
        this.profileExecutor = Objects.requireNonNull(profileExecutor, "profileExecutor must not be null");
        EnumMap<PipelinePhase, List<String>> copy = new EnumMap<>(PipelinePhase.class);
        for (Map.Entry<PipelinePhase, List<String>> entry : commandTemplates.entrySet()) {
            PipelinePhase phase = Objects.requireNonNull(entry.getKey(), "phase must not be null");
            List<String> template = Objects.requireNonNull(entry.getValue(), "command template must not be null");
            copy.put(phase, List.copyOf(template));
        }
        this.commandTemplates = Collections.unmodifiableMap(copy);
    }

    @Override
    public PhaseExecutionResult run(PipelinePhase phase, LoopCandidate candidate, LoopConfig config) {
        Objects.requireNonNull(phase, "phase must not be null");
        Objects.requireNonNull(candidate, "candidate must not be null");
        Objects.requireNonNull(config, "config must not be null");

        List<String> template = commandTemplates.get(phase);
        if (template == null) {
            return PhaseExecutionResult.failed("No command configured for phase " + phase.name());
        }
        if (template.isEmpty()) {
            return PhaseExecutionResult.success();
        }

        List<String> command = template.stream()
                .map(argument -> substitute(argument, phase, candidate, config))
                .toList();
        ProcessBuilder builder = new ProcessBuilder(command);
        builder.directory(config.projectRoot().toFile());

        try {
            PhaseExecutionResult profiledResult = runProfileBoundCommand(phase, candidate, config, command);
            if (profiledResult != null) {
                return profiledResult;
            }

            Process process = builder.start();
            StringBuilder stdout = new StringBuilder();
            StringBuilder stderr = new StringBuilder();
            Thread stdoutReader = Thread.startVirtualThread(
                    () -> readStream(process, true, stdout));
            Thread stderrReader = Thread.startVirtualThread(
                    () -> readStream(process, false, stderr));

            boolean finished = process.waitFor(config.iterationTimeoutSeconds(), TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                joinReader(stdoutReader);
                joinReader(stderrReader);
                return PhaseExecutionResult.timedOut("Phase " + phase.name()
                        + " command timed out after " + config.iterationTimeoutSeconds() + "s");
            }

            joinReader(stdoutReader);
            joinReader(stderrReader);
            int exitCode = process.exitValue();
            if (exitCode != 0) {
                return PhaseExecutionResult.failed("Phase " + phase.name()
                        + " command exited with code " + exitCode + commandOutput(stdout, stderr));
            }
            return PhaseExecutionResult.success();
        } catch (IOException e) {
            return PhaseExecutionResult.failed("Phase " + phase.name()
                    + " command failed to start: " + e.getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return PhaseExecutionResult.timedOut("Phase " + phase.name() + " command interrupted");
        }
    }

    private PhaseExecutionResult runProfileBoundCommand(PipelinePhase phase,
                                                        LoopCandidate candidate,
                                                        LoopConfig config,
                                                        List<String> command) throws IOException, InterruptedException {
        final class Holder {
            private java.util.Optional<ProfileBoundCommandExecutor.ExecutionResult> result = java.util.Optional.empty();
            private Exception failure;
        }
        Holder holder = new Holder();
        Thread executionThread = Thread.startVirtualThread(() -> {
            try {
                holder.result = profileExecutor.execute(config.projectRoot(), Map.of(), null, command);
            } catch (Exception e) {
                holder.failure = e;
            }
        });

        executionThread.join(TimeUnit.SECONDS.toMillis(config.iterationTimeoutSeconds()));
        if (executionThread.isAlive()) {
            executionThread.interrupt();
            executionThread.join(2000);
            return PhaseExecutionResult.timedOut("Phase " + phase.name()
                    + " command timed out after " + config.iterationTimeoutSeconds() + "s");
        }
        if (holder.failure != null) {
            if (holder.failure instanceof IOException ioException) {
                throw ioException;
            }
            if (holder.failure instanceof InterruptedException interruptedException) {
                throw interruptedException;
            }
            return PhaseExecutionResult.failed("Phase " + phase.name()
                    + " command failed to start: " + holder.failure.getMessage());
        }
        if (holder.result.isEmpty()) {
            return null;
        }
        ProfileBoundCommandExecutor.ExecutionResult result = holder.result.get();
        if (result.exitCode() != 0) {
            return PhaseExecutionResult.failed("Phase " + phase.name()
                    + " command exited with code " + result.exitCode() + commandOutput(
                            new StringBuilder(result.stdout()), new StringBuilder(result.stderr())));
        }
        return PhaseExecutionResult.success();
    }

    private static Map<PipelinePhase, List<String>> defaultCommandTemplates(List<String> commandPrefix) {
        Objects.requireNonNull(commandPrefix, "commandPrefix must not be null");
        if (commandPrefix.isEmpty()) {
            throw new IllegalArgumentException("commandPrefix must not be empty");
        }
        List<String> prefix = List.copyOf(commandPrefix);
        if (prefix.stream().anyMatch(value -> value == null || value.isBlank())) {
            throw new IllegalArgumentException("commandPrefix entries must not be blank");
        }

        EnumMap<PipelinePhase, List<String>> templates = new EnumMap<>(PipelinePhase.class);
        templates.put(PipelinePhase.RECOMMEND, List.of());
        for (Map.Entry<PipelinePhase, String> entry : DEFAULT_SUBCOMMANDS.entrySet()) {
            List<String> command = new ArrayList<>(prefix);
            command.add(entry.getValue());
            command.add("${changeName}");
            templates.put(entry.getKey(), command);
        }
        return templates;
    }

    private static String substitute(String argument,
                                     PipelinePhase phase,
                                     LoopCandidate candidate,
                                     LoopConfig config) {
        Map<String, String> values = new HashMap<>();
        values.put("phase", phase.name());
        values.put("phaseCommand", DEFAULT_SUBCOMMANDS.getOrDefault(phase, phase.name().toLowerCase()));
        values.put("changeName", candidate.changeName());
        values.put("milestoneFile", candidate.milestoneFile());
        values.put("milestoneGoal", candidate.milestoneGoal() != null ? candidate.milestoneGoal() : "");
        values.put("plannedChangeSummary", candidate.plannedChangeSummary());
        values.put("projectRoot", config.projectRoot().toAbsolutePath().toString());

        String result = argument;
        for (Map.Entry<String, String> entry : values.entrySet()) {
            result = result.replace("${" + entry.getKey() + "}", entry.getValue());
        }
        return result;
    }

    private static void readStream(Process process, boolean stdout, StringBuilder output) {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                stdout ? process.getInputStream() : process.getErrorStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                synchronized (output) {
                    if (!output.isEmpty()) {
                        output.append('\n');
                    }
                    output.append(line);
                }
            }
        } catch (IOException ignored) {
            // Process output is best-effort diagnostic data.
        }
    }

    private static void joinReader(Thread reader) throws InterruptedException {
        reader.join(2000);
    }

    private static String commandOutput(StringBuilder stdout, StringBuilder stderr) {
        String out;
        String err;
        synchronized (stdout) {
            out = stdout.toString();
        }
        synchronized (stderr) {
            err = stderr.toString();
        }
        if (out.isBlank() && err.isBlank()) {
            return "";
        }
        return " (stdout: " + abbreviate(out) + ", stderr: " + abbreviate(err) + ")";
    }

    private static String abbreviate(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        String normalized = value.replace('\n', ' ').trim();
        int maxLength = 500;
        return normalized.length() <= maxLength ? normalized : normalized.substring(0, maxLength) + "...";
    }
}
