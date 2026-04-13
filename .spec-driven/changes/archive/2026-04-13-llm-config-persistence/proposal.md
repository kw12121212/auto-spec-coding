# llm-config-persistence

## What

Define the next increment of M28 by adding durable storage for the default runtime LLM configuration snapshot, including version history and restart recovery semantics.

This proposal establishes the observable behavior for persisting non-sensitive runtime LLM config changes into Lealone DB, recovering the last valid default snapshot after restart, and retaining prior versions so later changes can restore a previous configuration safely.

## Why

The repository already has immutable runtime snapshot behavior from `dynamic-llm-config-snapshots`, but those snapshots are still memory-only. Without persistence, a service restart loses the active runtime LLM configuration and later roadmap items have no stable source of truth.

Scoping this change to persistence gives M28 a durable backbone before adding `SET LLM` SQL handling, config-change events, or M33 governance features. It also keeps the proposal small by avoiding new external command surfaces.

## Scope

In scope:
- persistence of the default runtime LLM config snapshot using Lealone DB
- recovery of the last valid persisted default snapshot when the runtime config store is initialized again
- version history for persisted default snapshots so earlier values remain available for internal restore behavior
- rollback/restore behavior as an internal capability over persisted versions
- unit-level observable behavior for persistence, recovery, version ordering, and failure isolation

Out of scope:
- persistence of session-scoped runtime snapshot overrides
- `SET LLM` SQL parsing or execution entry points
- config change event publication
- secret/vault integration, redaction, permission checks, or audit governance
- a new user-facing rollback API, SQL command, or HTTP endpoint

## Unchanged Behavior

Behaviors that must not change as a result of this change (leave blank if nothing is at risk):
- session-scoped runtime snapshot overrides remain runtime-only and continue to fall back to the default snapshot when no override exists
- in-flight request binding semantics from `dynamic-llm-config-snapshots` remain unchanged
- provider request serialization, streaming, retries, and tool-call behavior remain unchanged
- secret handling remains outside this change; only non-sensitive runtime config fields are persisted here
