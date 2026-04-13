# latest-lealone-alignment

## What

- Update the project to the latest Lealone upstream source baseline that can be built and verified from this repository.
- Adapt the project's direct Lealone integration points where upstream API or SPI drift prevents the repository from compiling, testing, or behaving as currently specified.
- Produce a compact compatibility and opportunity summary that records what was checked during the refresh and which additional upstream Lealone capabilities could help the project later.

## Why

- The current project already depends heavily on Lealone across embedded JDBC stores, service-executor SPI integration, JSON helpers, and source compilation. A plain `8.0.0-SNAPSHOT` label does not guarantee that the repository still matches the latest upstream source state.
- The most compatibility-sensitive integration points are already visible in the codebase: `LealoneSkillSourceCompiler` reaches into Lealone's compiler capability, `SkillServiceExecutorFactory` relies on Lealone SPI registration, and many `Lealone*Store` implementations depend on embedded JDBC and ORM JSON types.
- Refreshing to the latest upstream baseline now reduces hidden divergence, gives later roadmap work a more reliable Lealone foundation, and lets the repository capture upstream capabilities that can be adopted deliberately instead of opportunistically.

## Scope

- In scope:
- Identify and record the latest Lealone upstream source baseline used for this refresh.
- Update this repository so its Lealone dependency usage aligns with that latest baseline rather than relying on an ambiguous snapshot label alone.
- Check and adapt direct Lealone integration areas, including compiler integration, service-executor SPI integration, and embedded JDBC-backed stores.
- Add repo-local verification coverage and alignment notes so future refreshes can detect compatibility drift earlier.
- Land small, low-risk improvements that directly stabilize or simplify the current Lealone integration while preserving existing observable behavior.
- Summarize upstream Lealone capabilities that appear useful for this project, clearly separated from the work implemented in this change.
- Out of scope:
- Introducing a new platform abstraction layer such as the proposed M32 `LealonePlatform` milestone.
- Changing the observable SDK, JSON-RPC, HTTP, or store semantics beyond what is required to remain compatible with the refreshed Lealone baseline.
- Adding speculative integrations for upstream features that are only being evaluated in this change.
- Replacing Lealone with another backend or building a portability abstraction for non-Lealone runtimes.

## Unchanged Behavior

Behaviors that must not change as a result of this change (leave blank if nothing is at risk):
- Existing Native Java SDK, JSON-RPC, and HTTP REST API contracts remain as currently specified.
- Existing Lealone-backed stores, caches, vault behavior, and skill execution behavior remain semantically unchanged apart from compatibility fixes required by the refreshed upstream baseline.
- This change does not start the broader M32 platform-unification work or add a new cross-cutting runtime abstraction.
