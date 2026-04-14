# trusted-source-activation-gate

## What

Add a trusted-source activation gate to dynamic Skill hot-loading.

The change will require hot-load `load` and `replace` operations to verify that the requested `(skillName, sourceHash)` pair is trusted before the Java source can be compiled, read from class cache, registered as an active loader, or recorded as a failed compile/cache attempt. Trust is local and programmatic for this proposal: constructing code supplies the trusted source policy, and the gate rejects untrusted sources before activation side effects occur.

## Why

M34 exists because dynamic Java compilation and hot-loading can place new code on the activation path. The previous M34 changes made hot-loading disabled by default and permission-guarded, but an authorized caller can still provide arbitrary source unless the source itself is checked before activation.

This is the dependency-ordered next governance step before `hot-load-audit-log`: audit logging should record the final allow/reject outcomes after the source trust boundary is defined.

## Scope

In scope:
- a minimal trusted-source policy keyed by `skillName + sourceHash`
- trusted-source checks for `load` and `replace`
- fail-closed behavior when activation is enabled but no trusted-source policy is available
- a visible trusted-source rejection failure distinct from compiler/cache infrastructure failures and permission failures
- no-side-effect guarantees for rejected `load` and `replace` attempts
- `SkillAutoDiscovery` behavior when hot-loading is rejected by the trusted-source gate
- unit-test coverage for trusted source, untrusted source, missing policy, cache-hit, compile path, and discovery isolation scenarios

Out of scope:
- code signing infrastructure
- remote trust registries
- CLI, SQL, HTTP, SDK, or YAML configuration surfaces for managing trust
- full sandboxing of untrusted Java code
- audit log records for hot-load operations
- changes to `unload`, because unloading does not introduce new Java source into the activation path

## Unchanged Behavior

Behaviors that must not change as a result of this change (leave blank if nothing is at risk):
- hot-loading remains disabled by default unless the constructing code path explicitly enables it
- disabled hot-loading still rejects activation before permission, trust, compile, cache, or registry side effects
- permission checks for enabled `load` and `replace` still occur before the trusted-source gate
- permission-denied or confirmation-required operations still fail before trusted-source checks or compile/cache/registry side effects
- trusted and authorized `load` and `replace` operations keep existing compile-or-cache-hit behavior
- duplicate `load`, invalid source, cache miss, cache hit, failed-skill tracking, and class-loader isolation semantics remain unchanged after permission and source trust allow the operation
- `SkillAutoDiscovery` continues SQL registration when hot-load activation fails, including trust-gate rejection
