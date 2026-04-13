# llm-config-events

## What

Define the M28 event-publication increment for runtime LLM configuration changes.

This proposal specifies when the system publishes `LLM_CONFIG_CHANGED`, which successful runtime mutation paths are covered, and what minimum metadata downstream consumers can rely on when a new runtime snapshot becomes active for future requests.

## Why

The runtime LLM config milestone already promises config-change events, and the repository already has both an event system and centralized runtime mutation points. What is still missing is the observable contract that connects those two pieces.

Closing this gap finishes a concrete unfinished part of M28, makes runtime LLM changes visible to downstream consumers, and prepares later governance work in M33 without pulling secret handling, permission checks, or auditing into this proposal.

## Scope

In scope:
- success-only publication of `LLM_CONFIG_CHANGED` for `replaceDefaultSnapshot`, `replaceSessionSnapshot`, `applySetLlmStatement`, and `clearSessionSnapshot`
- event metadata for default-scope and session-scope runtime changes
- preserving existing atomic snapshot replacement, session isolation, and in-flight request binding semantics while adding event publication
- adding `LLM_CONFIG_CHANGED` to the public event type surface

Out of scope:
- failed-attempt events, audit records, or permission-denial observability
- secret references, secret redaction, vault integration, or config governance
- changing provider refresh semantics beyond observing already-committed runtime config updates
- startup recovery, persisted version restore, or other non-runtime mutation paths publishing this event
- including full old/new snapshot payloads in the event metadata contract

## Unchanged Behavior

Behaviors that must not change as a result of this change (leave blank if nothing is at risk):
- failed runtime config updates remain non-committing and do not change the active snapshot
- in-flight LLM requests remain bound to the snapshot they resolved before a later change succeeds
- default snapshot persistence and recovery semantics remain as already specified by M28 persistence work
- secret governance remains outside this proposal; the event contract covers only non-sensitive runtime config behavior
