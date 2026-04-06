# Design: tool-bash

## Approach

Create a single `BashTool` class in `org.specdriven.agent.tool` that implements the `Tool` interface. The tool wraps `ProcessBuilder` to spawn a bash process for each invocation.

Execution flow:
1. Extract parameters from `ToolInput` (`command`, optional `timeout`, optional `workDir`)
2. Call `ToolContext.permissionProvider().check()` with action `"execute"`, resource `"bash"`, and the command as a constraint
3. Build a `ProcessBuilder` with `/bin/bash -c <command>`, redirect stderr to stdout stream for merged capture
4. Start the process and set a deadline based on timeout
5. Read process output in a virtual thread, enforcing the timeout via `ProcessHandle`
6. On timeout, call `processHandle.destroyForcibly()` and return `ToolResult.Error` with a timeout message
7. On normal completion, return `ToolResult.Success` with captured output

## Key Decisions

1. **Shell invocation via `/bin/bash -c`** — allows complex shell syntax (pipes, redirects, env vars) without the tool needing to parse commands itself. On non-Linux systems, falls back to `sh -c`.
2. **Merged stdout/stderr** — follows the convention of most agent harnesses; the caller sees all output in one stream. Kept simple for M2.
3. **Default timeout 120s** — matches the spec-coding-sdk Go implementation default. Configurable per-invocation via the `timeout` parameter.
4. **Permission check before execution** — uses `PermissionProvider.check()` with a `Permission(action="execute", resource="bash", constraints={"command": <command>})`. The tool does NOT enforce policy itself — it only asks the provider.
5. **ProcessHandle for timeout** — JDK 25 provides `ProcessHandle` which gives a clean API for forceful termination.

## Alternatives Considered

- **Separate stdout/stderr in ToolResult** — more precise but adds complexity to the ToolResult type. Deferred; M2 scope keeps merged output.
- **Command allowlist/denylist in the tool** — rejected; this is policy enforcement, which belongs in the PermissionProvider implementation (M6). BashTool is a mechanism, not a policy layer.
- **Thread-based timeout with Thread.interrupt()** — ProcessHandle is cleaner and more reliable on JDK 25 than interrupt-based approaches.
