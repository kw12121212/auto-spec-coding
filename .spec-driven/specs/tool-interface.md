# Tool Interface Spec

## ADDED Requirements

### Requirement: Tool execution contract

- MUST have a unique name returned by `getName()`
- MUST declare its parameters via `getParameters()` returning `List<ToolParameter>`
- MUST accept `ToolInput` and `ToolContext` on execution
- MUST return `ToolResult` (either `Success` or `Error`)
- SHOULD provide a human-readable description via `getDescription()`

### Requirement: ToolInput record

- MUST be a Java record with field: `parameters` (Map<String, Object>)
- MAY be extended with additional fields in future changes without breaking signature

### Requirement: ToolParameter descriptor

- MUST be a Java record with fields: `name` (String), `type` (String), `description` (String), `required` (boolean)

### Requirement: ToolResult sealed type

- MUST be a sealed interface with exactly two implementations: `Success` and `Error`
- `Success` MUST carry an output String
- `Error` MUST carry an error message String and an optional throwable cause

### Requirement: ToolContext

- MUST provide the current working directory
- MUST provide access to a `PermissionProvider`
- MAY provide environment variables

### Requirement: Structured permission decision handling

- Permission enforcement is centralized in the orchestrator via `ToolExecutionHook` — individual tools do not perform permission checks
- The `PermissionCheckHook` enforces permission decisions for all tool invocations

### Requirement: Tool permission declaration

- `Tool` MUST provide a default method `Permission permissionFor(ToolInput input, ToolContext context)`
- The default implementation MUST return `new Permission("execute", getName(), Map.of())`
- A tool that requires specific permission semantics (e.g., distinguishing read vs write actions, or extracting the target resource from input) MUST override this method
- The returned Permission MUST be consistent with the tool's actual operation — `action` and `resource` MUST accurately reflect what the tool does with the given input
