# Tool Interface Spec (delta)

## ADDED Requirements

### Requirement: Tool permission declaration

- `Tool` MUST provide a default method `Permission permissionFor(ToolInput input, ToolContext context)`
- The default implementation MUST return `new Permission("execute", getName(), Map.of())`
- A tool that requires specific permission semantics (e.g., distinguishing read vs write actions, or extracting the target resource from input) MUST override this method
- The returned Permission MUST be consistent with the tool's actual operation — `action` and `resource` MUST accurately reflect what the tool does with the given input
