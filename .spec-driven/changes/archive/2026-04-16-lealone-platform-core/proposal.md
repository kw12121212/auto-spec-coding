# lealone-platform-core

## What

Define the first `LealonePlatform` foundation as a public platform entry point that unifies access to the repository's existing Lealone-centered capability domains without yet introducing the full lifecycle, health, metrics, or migration scope of later M32 changes.

This proposal adds a minimal typed capability surface for four already-existing domains: database access, runtime LLM configuration/registry access, skill compilation and hot-load infrastructure, and interactive session creation. It also updates the SDK public contract so `SpecDriven` remains the primary agent facade while `LealonePlatform` becomes an additional public platform-level entry point.

## Why

The roadmap shows a clear dependency chain: M36 depends on M32, and M37 depends on M36. Within M32, `lealone-platform-core` is the earliest foundation change because the later M32 items assume that a stable platform boundary already exists.

Today the codebase already contains the ingredients of a Lealone-centered platform, but they are assembled ad hoc across `SdkBuilder`, `SpecDriven`, runtime LLM configuration classes, skill compilation/hot-load classes, and interactive session infrastructure. Without a first-class platform surface, later work on unified configuration lifecycle, health checks, metrics, and application runtime bootstrapping would have to keep adding new glue code in multiple places.

This change creates the smallest platform contract that can anchor those later changes while avoiding speculative abstraction.

## Scope

- In scope:
- Define `LealonePlatform` as a public platform-level entry point in `org.specdriven.sdk` or an adjacent public package aligned with the SDK surface
- Define a minimal typed capability contract for the four milestone domains already evidenced in the repository: DB, LLM runtime, compiler/hot-load, and interactive session
- Define how callers obtain those capabilities from one assembled platform instance
- Define compatibility expectations between `LealonePlatform` and the existing `SpecDriven` agent facade
- Add unit-testable observable requirements for typed capability access and stable platform assembly behavior

- Out of scope:
- Unified configuration center semantics from `platform-config-lifecycle`
- Aggregated health endpoints or metrics collection from `platform-health-metrics`
- Migration adapters from `platform-migration-adapters`
- New workflow, service runtime, `services.sql`, or business application runtime behavior from M36/M37
- Replacing existing implementations of LLM runtime config, skill compilation, hot-loading, interactive session handling, or JDBC-backed Lealone stores
- Introducing a generic plugin-style capability registry before a concrete need is proven

## Unchanged Behavior

Behaviors that must not change as a result of this change (leave blank if nothing is at risk):

- Existing `SpecDriven.builder()` and `SdkBuilder.build()` agent-facing behavior remains valid
- Existing runtime LLM config semantics, event publication, and permission behavior remain unchanged
- Existing skill compilation and hot-load behavior remain unchanged
- Existing interactive session lifecycle and loop bridge behavior remain unchanged
- Existing JDBC/Lealone-backed stores continue to own their current persistence behavior; this change only introduces a shared platform access contract
