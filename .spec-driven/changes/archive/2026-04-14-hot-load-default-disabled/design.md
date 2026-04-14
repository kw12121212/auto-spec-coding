# Design: hot-load-default-disabled

## Approach

Add a minimal programmatic activation gate to the existing hot-loader contract instead of introducing a new external configuration surface. A newly constructed hot-loader starts disabled, rejects `load` and `replace` activation attempts with a normal failure result, and performs no compile/cache/registry side effects until explicitly enabled.

Keep discovery behavior resilient: `SkillAutoDiscovery` should still attempt SQL registration even when a configured hot-loader is disabled. Disabled activation is treated as a per-skill hot-load failure, not as a system-wide discovery failure.

## Key Decisions

- Keep enablement programmatic-only in this first governance change because the current codebase has no stable user-facing contract for hot-load activation.
- Reject disabled activation through ordinary `SkillLoadResult.success = false` semantics instead of exceptions so callers can continue partial-success flows such as discovery.
- Leave executor fallback behavior unchanged: if no active hot-loaded executor exists, service execution still uses the existing non-hot-loaded implementation path.
- Limit this proposal to the default-disabled baseline and defer permissions, trusted-source checks, and audit recording to the later M34 planned changes already listed in the roadmap.

## Alternatives Considered

- Add a new YAML or CLI enable switch now. Rejected because this proposal would then need to define and stabilize an external enablement surface that does not yet exist in the current specs.
- Treat disabled activation as an exception path. Rejected because discovery currently supports partial success and should keep processing other skills and SQL registration even when hot-loading is unavailable.
- Fold permission and trusted-source governance into this first change. Rejected because the roadmap already decomposes M34 into smaller planned changes, and combining them would expand scope prematurely.
