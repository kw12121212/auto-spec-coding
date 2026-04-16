# Design: lealone-platform-core

## Approach

Introduce a thin `LealonePlatform` assembly surface that groups already-existing repository capabilities into one public platform object with typed accessors. The platform contract stays intentionally narrow:

1. It assembles or carries references to the four known capability domains called out by M32.
2. It exposes those domains through explicit typed methods rather than a generic lookup registry.
3. It does not redefine the internal behavior of those capabilities; it only standardizes how a caller obtains them from one place.

The first platform core should describe observable behavior such as:
- a caller can create or obtain a `LealonePlatform` instance from the supported public entry path
- the platform exposes stable typed access to the DB capability
- the platform exposes stable typed access to the runtime LLM capability
- the platform exposes stable typed access to the compiler or hot-load capability set
- the platform exposes stable typed access to interactive-session creation capability
- existing SDK agent behavior remains compatible when the platform entry point is introduced

For repository alignment, the typed platform capability set should be expressed in terms of already evidenced contracts rather than new abstract domains invented for the proposal. The current codebase already shows suitable anchors:
- DB capability: Lealone/JDBC-backed persistence infrastructure already used across stores
- LLM capability: `LlmProviderRegistry` and `RuntimeLlmConfigStore`
- Compiler capability: `SkillSourceCompiler`, `ClassCacheManager`, and `SkillHotLoader`
- Interactive capability: `InteractiveSessionFactory`

## Key Decisions

1. Public platform entry in addition to `SpecDriven`

The user explicitly chose to expose a public platform entry now. This changes the existing SDK public contract from "sole public entry point" to a dual-surface model: `SpecDriven` remains the primary agent facade, and `LealonePlatform` becomes the public platform assembly surface.

2. Typed capability access instead of a generic registry

The first platform core will use typed accessors for the four known capability domains. This matches the current repository evidence, keeps the API concrete, and avoids speculative key-based registries before dynamic capability expansion is needed.

3. Thin glue layer, not a second architecture

The platform core does not replace `DefaultLlmProviderRegistry`, `LealoneSkillHotLoader`, `InteractiveSessionFactory`, or the existing Lealone-backed stores. It only defines a shared access contract around them. This preserves the milestone note that M32 must remain a thin glue layer.

4. Minimal spec impact for foundation stage

This proposal should add one dedicated platform spec and one SDK public-surface modification. It should not spread observable changes across config, LLM, interactive, or event specs unless the platform core truly changes those user-visible behaviors.

## Alternatives Considered

1. Keep `LealonePlatform` internal-only for now

Rejected because the user explicitly wants a public entry, and later milestones benefit from a stable public platform contract rather than continuing ad hoc assembly.

2. Use a generic capability registry as the first API

Rejected because M32 currently names four concrete domains, and the repository already has concrete types for each. A generic registry would add abstraction cost before there is evidence of dynamic capability discovery needs.

3. Fold platform behavior directly into `SpecDriven`

Rejected because `SpecDriven` is the agent facade, while M32 needs a broader platform surface that can later support unified lifecycle, health, metrics, and service runtime integration without making the SDK agent API carry every platform concern.

4. Expand this proposal to include config lifecycle or health checks

Rejected because those are already planned as later M32 changes. Pulling them into `lealone-platform-core` would blur milestone boundaries and weaken the value of the roadmap decomposition.
