# Tasks: tool-bash

## Implementation

- [x] Create `BashTool.java` in `org.specdriven.agent.tool` implementing `Tool` interface
- [x] Implement `getName()` returning `"bash"`, `getDescription()`, and `getParameters()` declaring command/timeout/workDir
- [x] Implement `execute()` — extract parameters, validate `command` is non-empty
- [x] Implement permission check via `ToolContext.permissionProvider().check()` before execution
- [x] Implement ProcessBuilder invocation with shell (`/bin/bash -c` or `sh -c` fallback)
- [x] Implement timeout enforcement using ProcessHandle with configurable timeout
- [x] Implement output capture (merged stdout/stderr) and result mapping (exit code → Success/Error)

## Testing

- [x] `BashToolTest.java` — test `getName()`, `getDescription()`, `getParameters()` return correct values
- [x] Test happy path: execute `echo hello` returns `ToolResult.Success` with `"hello"`
- [x] Test working directory: execute `pwd` with custom workDir and verify output
- [x] Test timeout: execute a long-running command (`sleep 10`) with timeout=1, verify `ToolResult.Error` with timeout message
- [x] Test permission denied: mock PermissionProvider returning `false`, verify `ToolResult.Error` without execution
- [x] Test missing command parameter returns `ToolResult.Error`
- [x] Test non-zero exit code returns `ToolResult.Error` with relevant message
- [x] `mvn test` passes

## Verification

- [x] Verify implementation matches proposal scope (no extra features)
- [x] Verify BashTool conforms to Tool interface contract in tool-interface.md spec
- [x] Verify permission integration matches permission-interface.md spec
