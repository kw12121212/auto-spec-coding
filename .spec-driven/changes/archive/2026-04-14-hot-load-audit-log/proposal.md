# hot-load-audit-log

## What

Add audit-event coverage for dynamic Skill hot-loading operations.

The change will make hot-load `load`, `replace`, and `unload` attempts produce structured audit events through the existing event/audit surface. `load` and `replace` audit records will also identify whether the activation attempt reached compilation, reused cache output, failed before compilation, or failed during compiler/cache infrastructure handling.

## Why

M34 exists because dynamic Java compilation and hot-loading can place new code on the activation path. The previous M34 changes made hot-loading disabled by default, permission-guarded, and trusted-source-gated, but operators still need a durable trace of who attempted activation changes and what happened.

This is the final planned M34 governance item. It closes the auditability gap after the activation gates have already defined which hot-load operations are allowed or rejected.

## Scope

In scope:
- a public event type for hot-load operation audit records
- audit events for `load`, `replace`, and `unload` attempts
- audit metadata covering operation, skill name, result, requester when known, source hash for source-bearing operations, and failure reason category when an operation fails or is rejected
- compile/cache phase visibility for `load` and `replace` without logging raw Java source
- unit-test coverage for successful activation, disabled activation, permission rejection, trusted-source rejection, compilation diagnostics failure, infrastructure failure, and unload

Out of scope:
- full sandboxing of untrusted Java code
- code-signing infrastructure or remote trust registries
- new secret or log-storage backends
- a separate audit-log query API beyond the existing event/audit system
- broad logging changes outside dynamic Skill hot-loading
- changing permission, trust, cache, registry, or class-loader behavior

## Unchanged Behavior

Behaviors that must not change as a result of this change (leave blank if nothing is at risk):
- hot-loading remains disabled by default unless the constructing code path explicitly enables it
- disabled hot-loading still avoids permission, trust, compile, cache, active-registry, and failed-registry side effects
- permission checks for enabled `load`, `replace`, and `unload` still preserve `ALLOW`, `DENY`, and `CONFIRM` meanings
- trusted-source checks for enabled `load` and `replace` still occur before compile/cache/registry side effects after permission allows the request
- trusted and authorized `load` and `replace` operations keep existing compile-or-cache-hit behavior
- `unload` continues to require permission but not source trust
- raw Java source text is never recorded in event metadata or audit log records
