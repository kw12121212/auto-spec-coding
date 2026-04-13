# skill-source-compiler

## What

- Define the first M30 change that introduces a minimal `SkillSourceCompiler` contract for compiling Java skill source strings through Lealone's compiler capability.
- Define the compile result, diagnostic, and infrastructure-failure boundary needed by later `class-cache-manager`, `skill-hot-loader`, and `compile-fallback-isolation` changes.

## Why

- M30's later planned changes all depend on a stable compile-facing contract before cache persistence, hot replacement, or failure-isolation behavior can be specified safely.
- The repository already has skill SQL conversion, discovery, and execution specs, but it does not yet define how Java skill source becomes compiled class output inside the project boundary.
- Keeping this change narrow avoids mixing foundational compiler behavior with dependency upgrades, governance, or runtime activation work.

## Scope

- In scope:
- Define the minimal compiler abstraction for Java skill source strings and caller-controlled class output directories.
- Define observable success, diagnostic, and infrastructure-failure behavior for compilation.
- Define the Lealone-backed compiler adapter expected by this project.
- Verify whether the current `com.lealone` dependency already exposes the compiler capability required by this contract.
- Out of scope:
- Class artifact persistence strategy, cache invalidation, and source-hash keying.
- Runtime class loading, unloading, replacement, or registration of compiled skills.
- Permission gates, default-disable policy, trusted-source checks, and audit logging.
- Treating Lealone version alignment or dependency upgrade as the primary goal of this change.

## Unchanged Behavior

Behaviors that must not change as a result of this change (leave blank if nothing is at risk):
- Existing `SkillSqlConverter`, `SkillAutoDiscovery`, and `SkillServiceExecutor` behavior remains unchanged.
- Static skill registration and execution through `SKILL.md` and `CREATE SERVICE` remain the only supported skill activation path.
- No new external command surface or admin workflow is introduced by this proposal.
