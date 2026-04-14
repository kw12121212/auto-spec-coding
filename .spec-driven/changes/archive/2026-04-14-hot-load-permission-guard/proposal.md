# hot-load-permission-guard

## What

Add permission enforcement to the runtime Skill hot-load activation path.

The change will require dynamic skill `load`, `replace`, and `unload` operations to pass through the existing `PermissionProvider` / `PermissionContext` model before they compile source, read or populate the class cache, register a loader, replace an active loader, or remove an active loader. Permission decisions other than `ALLOW` will stop the operation before side effects occur.

## Why

M34 exists because dynamic compilation and hot-loading are intentionally powerful: once enabled, they can place new Java code on the activation path. The previous `hot-load-default-disabled` change made the feature opt-in, but opt-in alone does not distinguish a trusted administrator from an untrusted caller after activation.

This change is the next dependency-ordered governance step: it reuses the project's existing permission model before adding later trusted-source activation gates or audit logging.

## Scope

In scope:
- permission checks for Skill hot-load `load`, `replace`, and `unload` operations
- permission action/resource conventions for hot-load operations
- failure behavior for `DENY` and `CONFIRM` decisions
- preserving no-side-effect behavior when permission is denied or confirmation is required
- `SkillAutoDiscovery` interaction with the guarded hot-loader path when Java executor source is present
- unit-test coverage for allowed, denied, confirmation-required, and discovery integration paths

Out of scope:
- trusted source validation before activation
- audit log records for hot-load operations
- full sandboxing of untrusted Java code
- new external enablement surfaces such as YAML, SQL, CLI, HTTP, or SDK commands
- broad replacement of the existing permission model

## Unchanged Behavior

Behaviors that must not change as a result of this change (leave blank if nothing is at risk):
- hot-loading remains disabled by default unless the constructing code path explicitly enables it
- disabled hot-loading still rejects activation before compile/cache/registry side effects
- successful authorized `load` and `replace` operations keep existing compile-or-cache-hit behavior
- duplicate `load`, invalid source, cache miss, cache hit, failed-skill tracking, and class-loader isolation semantics remain unchanged after permission allows the operation
- `SkillAutoDiscovery` without a configured `SkillHotLoader` continues to preserve SQL registration behavior
- permission model meanings for `ALLOW`, `DENY`, and `CONFIRM` remain unchanged
