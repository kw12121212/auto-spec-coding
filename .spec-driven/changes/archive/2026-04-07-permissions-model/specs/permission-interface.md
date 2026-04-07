# Permission Interface Spec

## MODIFIED Requirements

### Requirement: PermissionProvider contract

- MUST define `check(Permission, PermissionContext)` returning `PermissionDecision`
- MUST define `grant(Permission, PermissionContext)`
- MUST define `revoke(Permission, PermissionContext)`
- MUST treat `PermissionDecision.ALLOW` as permission granted
- MUST treat `PermissionDecision.DENY` as permission rejected without further confirmation
- MUST treat `PermissionDecision.CONFIRM` as permission requiring an explicit approval step from a later hook or interface layer before execution may proceed

## ADDED Requirements

### Requirement: PermissionDecision enum

- MUST define exactly three outcomes: `ALLOW`, `DENY`, and `CONFIRM`
- `ALLOW` MUST mean the requested operation may proceed immediately
- `DENY` MUST mean the requested operation must not proceed
- `CONFIRM` MUST mean the requested operation is permitted only after an explicit confirmation step outside the core permission model

### Requirement: Default permission policy behavior

- The default permission policy MUST evaluate permissions deterministically from the requested action, resource, and context
- The default permission policy MUST return `CONFIRM` for bash execution requests
- The default permission policy MUST return `CONFIRM` for file mutation requests, including write and edit operations
- The default permission policy MUST return `ALLOW` for read and search operations targeting paths inside the active working directory tree
- The default permission policy MUST return `DENY` for read and search operations targeting paths outside the active working directory tree
- The default permission policy MAY be extended by later changes, but those extensions MUST preserve the observable meanings of `ALLOW`, `DENY`, and `CONFIRM`
