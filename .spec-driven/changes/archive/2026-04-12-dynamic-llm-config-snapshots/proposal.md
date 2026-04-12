# dynamic-llm-config-snapshots

## What

Define the first increment of M28 by introducing immutable runtime LLM configuration snapshots and atomic replacement semantics for future requests.

This proposal establishes the observable behavior for creating, reading, and replacing non-sensitive LLM config snapshots without requiring service restart, while keeping in-flight requests bound to the snapshot they started with.

## Why

M28 is the dependency root for dynamic LLM configuration. The remaining planned changes in M28 depend on a stable snapshot model before persistence, `SET LLM` SQL handling, provider refresh, and change events can be specified safely.

Scoping this change to snapshot behavior reduces sequencing risk and creates a clear contract for later milestones, especially M33 secret governance and M32 platform unification.

## Scope

In scope:
- runtime representation of non-sensitive LLM config as immutable snapshots
- atomic replacement behavior so later requests observe the new snapshot
- session-scoped snapshot resolution behavior for future session-specific updates
- provider/client behavior that binds each request to one snapshot for its lifetime
- unit-level observable behavior for concurrent reads and snapshot replacement

Out of scope:
- database persistence or recovery of snapshots
- `SET LLM` SQL parsing or execution
- event publication for config changes
- secret/vault integration, permission checks, or audit governance
- adding new LLM providers or changing request/response protocol behavior

## Unchanged Behavior

Behaviors that must not change as a result of this change (leave blank if nothing is at risk):
- existing static `LlmConfig` parsing and provider registration behavior continue to work until callers opt into runtime snapshots
- existing request serialization, streaming, retry, and tool-calling semantics remain unchanged
- no secret fields are added to normal config persistence or event flows in this change
