# permissions-model

## What

Define the core permission decision model used by tools and agent runtime components. Replace the current boolean-only permission contract with a structured decision that can represent `allow`, `deny`, and `confirm`, and specify the default policy semantics for existing tool categories.

## Why

The repository already performs permission checks in all shipped tools, but the current spec only models a boolean result even though milestone M6 explicitly requires three outcomes: allow, deny, and confirmation-required. Establishing the core model now gives later changes a stable foundation for interception hooks, interface-layer prompts, and persistent policy storage without requiring another breaking spec revision.

## Scope

- Update the permission spec to define a structured decision type and permission evaluation contract
- Define how tool-facing permission checks consume that decision model
- Specify default permission behavior for the currently implemented tool categories: bash, file operations, grep, and glob
- Clarify the observable permission-denied and confirmation-required behavior exposed by tools
- Keep implementation planning limited to in-memory/default policy behavior needed by the core model

Out of scope:
- Wiring permission checks into the full agent orchestration loop (`permissions-hooks`)
- Persistent policy storage, runtime grant/revoke administration, or audit log persistence (`permission-policy-store`)
- Interface-specific confirmation UX in SDK, JSON-RPC, or HTTP layers

## Unchanged Behavior

Behaviors that must not change as a result of this change (leave blank if nothing is at risk):

- Tool names, parameter schemas, and non-permission execution semantics remain unchanged
- Existing tool specs continue to require a permission check before performing protected operations
- Later milestones remain responsible for interception wiring, persistence, and transport-specific confirmation flows
