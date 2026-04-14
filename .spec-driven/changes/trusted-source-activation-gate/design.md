# Design: trusted-source-activation-gate

## Approach

Introduce a small hot-load source trust boundary in the `org.specdriven.skill.hotload` package. The boundary is evaluated only for activation operations that introduce Java source into the runtime path: `load` and `replace`.

The trust decision uses the already-computed hot-load key: `skillName` and `sourceHash`. The gate runs after the existing default-disabled and permission checks, and before cache lookup, cache directory creation, compilation, active registry mutation, or failed-skill registry mutation. This preserves the previous security order: disabled activation short-circuits first, permission authorizes the caller next, and source trust authorizes the exact source to be activated.

The first implementation should keep trust local and programmatic. Constructing code supplies a trusted-source policy to `LealoneSkillHotLoader`; there is no new external management surface in this change. A missing policy fails closed when activation is enabled and a `load` or `replace` operation reaches the trust boundary.

`SkillAutoDiscovery` already computes `sourceHash` before invoking the hot-loader. If the trust gate rejects a discovered executor source, discovery should record a per-skill hot-load failure and continue SQL registration, matching the existing disabled-hot-loader and permission-rejection behavior.

## Key Decisions

- Use `skillName + sourceHash` as the trust key. It is deterministic, already available in the hot-load flow, and avoids treating arbitrary source text as trusted because the caller is authorized.
- Keep the trust policy local/programmatic. The roadmap calls for a governance gate, but not for a public management surface; adding CLI/HTTP/SQL/YAML controls here would expand scope.
- Run trust checks after permission checks. Permission answers "may this caller perform the operation"; source trust answers "may this exact source be activated". Keeping this order preserves the no-side-effect behavior already defined for permission rejection.
- Treat missing trusted-source policy as fail-closed for enabled activation. Enabled hot-loading should not silently treat every source as trusted.
- Do not apply the trust gate to `unload`. Unload removes an active loader and does not compile or activate new source.
- Expose trusted-source rejection as a hot-load trust failure, not as a compiler diagnostic or cache failure. The rejection is a governance decision, not a compilation or infrastructure problem.

## Alternatives Considered

- Trust any source when the permission provider returns `ALLOW`. Rejected because caller authorization and source authorization are separate governance boundaries.
- Use a source path allowlist as the primary trust key. Rejected as the default because the same path can contain different content over time; `sourceHash` gives a stronger activation identity. Discovery may still use path information in error context.
- Add code signing now. Rejected because M34 explicitly excludes larger sandbox/trust infrastructure and this change only needs a local activation gate.
- Add CLI, SQL, HTTP, SDK, or YAML trust management now. Rejected because the current hot-load enablement model is programmatic-only and the roadmap item does not require a new external management surface.
- Fold audit logging into this change. Rejected because `hot-load-audit-log` is a separate planned M34 item and should build on the finalized trust outcome semantics.
