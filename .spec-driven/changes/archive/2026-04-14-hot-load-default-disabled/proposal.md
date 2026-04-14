# hot-load-default-disabled

## What

- Define the first governance step for M34 by requiring dynamic skill compilation and hot-loading to be explicitly enabled before it can activate Java executors.
- Keep the accepted enablement model programmatic-only for this proposal, with no new YAML, SQL, CLI, HTTP, or SDK command surface.

## Why

- M30 added working dynamic compilation and hot-loading, but the roadmap still lacks the safety baseline that this powerful capability is off by default.
- Landing the default-disabled contract first reduces operational risk and gives later M34 permission, trust-source, and audit changes a stable base behavior to extend.

## Scope

- In scope:
- Define that hot-loading is disabled by default unless the constructing code path explicitly enables it.
- Define the observable behavior for `SkillHotLoader` when hot-loading is disabled.
- Define how `SkillAutoDiscovery` behaves when a hot-loader instance is present but not enabled for activation.
- Keep SQL skill registration and non-hot-loaded executor fallback behavior intact.
- Out of scope:
- New user-facing configuration or command surfaces for enablement.
- Permission checks, trusted-source validation, and audit logging for hot-load operations.
- Sandboxing or broader execution isolation for dynamically compiled code.

## Unchanged Behavior

Behaviors that must not change as a result of this change (leave blank if nothing is at risk):
- Existing compilation, cache-hit, replace, unload, and failure-reporting behavior remains unchanged when hot-loading is explicitly enabled.
- `SkillAutoDiscovery` continues to discover skills and register generated SQL even when hot-loading is unavailable or disabled.
- `SkillServiceExecutorFactory` continues to fall back to the standard `SkillServiceExecutor` when no active hot-loaded executor is available.
