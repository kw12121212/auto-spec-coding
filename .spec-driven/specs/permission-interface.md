# Permission Interface Spec

## ADDED Requirements

### Requirement: PermissionProvider contract

- MUST define `check(Permission, PermissionContext)` returning boolean
- MUST define `grant(Permission, PermissionContext)`
- MUST define `revoke(Permission, PermissionContext)`

### Requirement: Permission record

- MUST be a Java record with fields: `action` (String), `resource` (String), `constraints` (Map<String, String>)

### Requirement: PermissionContext record

- MUST be a Java record with fields: `toolName` (String), `operation` (String), `requester` (String)
