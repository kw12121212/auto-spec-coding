# Design: hot-load-integration

## Approach

This change wires the existing hot-load primitives into the current skill discovery and
execution path without changing the underlying SQL registration contract.

`SkillAutoDiscovery` keeps its current "discover direct child skill directories, parse
`SKILL.md`, register SQL service" flow. The only addition is an optional
`SkillHotLoader` dependency. When present, discovery looks for a Java source file next
to `SKILL.md` whose class name matches the executor class already implied by
`SkillSqlConverter`: `org.specdriven.skill.executor.<PascalCase(skill-name)>Executor`.
If that source file exists, discovery reads it, computes a deterministic source hash,
and calls `hotLoader.load(...)` before registering the SQL service. A hot-load failure
is reported in `DiscoveryResult.hotLoadFailedCount` and appended to `errors`, but it
does not change SQL registration semantics.

`SkillServiceExecutorFactory` also gains an optional `SkillHotLoader`. When the service
being instantiated corresponds to a hot-loaded skill, the factory resolves the service
name from `Service.getName()`, asks the hot-loader for the active `ClassLoader`, loads
the exact executor class named by `service.getImplementBy()`, and instantiates it.
When the hot-loader is absent or has no active loader for that service, the existing
factory behavior remains unchanged.

## Key Decisions

**1. Keep hot-load optional at both integration points**
This preserves the unchanged-behavior requirement: existing discovery and executor
flows continue to work when no hot-loader is configured.

**2. Reuse the existing executor naming convention**
The entry class name is derived from the current `SkillSqlConverter` output rather than
adding a new frontmatter field. That keeps the change small and avoids expanding the
observable skill metadata surface.

**3. Treat hot-load failures as additive reporting, not SQL failures**
`registeredCount`, `failedCount`, and the existing SQL path semantics remain unchanged.
The new counters report hot-load outcomes independently.

**4. Source discovery is file-based and local to a skill directory**
Discovery only hot-loads when a Java source file is physically present beside the
existing `SKILL.md` skill assets. It does not recurse, scan arbitrary source trees, or
change the established discovery boundary.

## Alternatives Considered

**Add executor class metadata to frontmatter**
Rejected because the current SQL converter already defines the observable executor
class naming contract. Adding new frontmatter would widen scope unnecessarily.

**Instantiate a generic wrapper instead of the hot-loaded executor class**
Rejected because the proposal explicitly requires that the factory prefer the
hot-loader's `ClassLoader` when instantiating the skill's executor.

**Count hot-load failures inside `failedCount`**
Rejected because the proposal's unchanged-behavior section preserves the current SQL
meaning of `registeredCount`, `failedCount`, and `errors` for registration outcomes.
