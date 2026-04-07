package org.specdriven.agent.tool;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.specdriven.agent.permission.Permission;

/**
 * Tool that executes shell commands via the system shell.
 */
public class BashTool implements Tool {

    private static final int DEFAULT_TIMEOUT_SECONDS = 120;
    private static final String NAME = "bash";

    private static final List<ToolParameter> PARAMETERS = List.of(
            new ToolParameter("command", "string", "The shell command to execute", true),
            new ToolParameter("timeout", "integer", "Timeout in seconds (default: 120)", false),
            new ToolParameter("workDir", "string", "Working directory for the command (default: context workDir)", false)
    );

    @Override
    public String getName() {
        return NAME;
    }

    @Override
    public String getDescription() {
        return "Executes a shell command via the system shell and returns the combined output";
    }

    @Override
    public List<ToolParameter> getParameters() {
        return PARAMETERS;
    }

    @Override
    public Permission permissionFor(ToolInput input, ToolContext context) {
        Object commandObj = input.parameters().get("command");
        String command = commandObj != null ? commandObj.toString() : "";
        return new Permission("execute", "bash", Map.of("command", command));
    }

    @Override
    public ToolResult execute(ToolInput input, ToolContext context) {
        // Extract and validate command parameter
        Object commandObj = input.parameters().get("command");
        if (commandObj == null || commandObj.toString().isBlank()) {
            return new ToolResult.Error("Missing or empty required parameter: command");
        }
        String command = commandObj.toString();

        // Resolve timeout
        int timeoutSeconds = DEFAULT_TIMEOUT_SECONDS;
        Object timeoutObj = input.parameters().get("timeout");
        if (timeoutObj != null) {
            timeoutSeconds = ((Number) timeoutObj).intValue();
        }

        // Resolve working directory
        String workDir = resolveStringParam(input, "workDir", context.workDir());

        // Determine shell
        String shell = detectShell();

        // Execute
        try {
            ProcessBuilder pb = new ProcessBuilder(shell, "-c", command);
            pb.directory(Path.of(workDir).toFile());
            pb.redirectErrorStream(true);

            Process process = pb.start();
            ProcessHandle handle = process.toHandle();

            // Read output in a separate thread so we can enforce timeout
            StringBuilder output = new StringBuilder();
            Thread readerThread = Thread.startVirtualThread(() -> {
                try (BufferedReader reader = new BufferedReader(
                        new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        if (!output.isEmpty()) {
                            output.append('\n');
                        }
                        output.append(line);
                    }
                } catch (IOException ignored) {}
            });

            boolean finished = process.waitFor(timeoutSeconds, TimeUnit.SECONDS);
            if (!finished) {
                handle.destroyForcibly();
                readerThread.join(2000);
                return new ToolResult.Error("Command timed out after " + timeoutSeconds + "s: " + command);
            }

            readerThread.join(2000);
            int exitCode = process.exitValue();

            if (exitCode != 0) {
                return new ToolResult.Error("Command exited with code " + exitCode + ": " + output);
            }

            return new ToolResult.Success(output.toString());

        } catch (IOException e) {
            return new ToolResult.Error("Failed to start process: " + e.getMessage(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return new ToolResult.Error("Execution interrupted: " + e.getMessage(), e);
        }
    }

    private static String detectShell() {
        if (Path.of("/bin/bash").toFile().exists()) {
            return "/bin/bash";
        }
        return "sh";
    }

    private static String resolveStringParam(ToolInput input, String paramName, String defaultValue) {
        Object value = input.parameters().get(paramName);
        return value != null ? value.toString() : defaultValue;
    }
}
