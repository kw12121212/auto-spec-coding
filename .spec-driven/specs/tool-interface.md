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
