# workflow-runtime-contract

## What

- Define the first workflow runtime contract for the repository, covering workflow declaration, start, state query, result retrieval, and lifecycle visibility.
- Add aligned SDK, HTTP REST, and JSON-RPC surface requirements for starting and observing workflow instances.
- Define parity between direct domain declaration and the first supported Lealone SQL workflow declaration path without yet specifying workflow step composition, human-bridge mechanics, or recovery policy.

## Why

- M37 needs a stable workflow identity and lifecycle contract before later changes can safely define service/tool/agent composition, human bridging, and recovery behavior.
- The repository already has service runtime, question handling, interactive session, event audit, and multi-surface API foundations; without an explicit workflow contract, later M37 changes would have to invent workflow semantics piecemeal across SDK, HTTP, and JSON-RPC.
- Locking the minimal contract now preserves dependency order and reduces the risk of accidentally coupling business workflow runtime to the existing spec-driven development loop.

## Scope

- In scope:
  - The first observable workflow declaration contract for both SDK/domain and governed Lealone SQL paths.
  - Workflow instance lifecycle states, start behavior, state query, and result retrieval.
  - SDK, HTTP REST, and JSON-RPC public surface requirements for those workflow lifecycle operations.
  - Workflow lifecycle audit and event visibility needed by later M37 changes.
- Out of scope:
  - Detailed service/tool/agent step composition semantics.
  - Question/mobile/interactive bridge mechanics beyond reserving observable waiting-state lifecycle space.
  - Checkpoint recovery, retry policy, operator repair flows, or workflow governance beyond the first lifecycle contract.
  - Production install/repair or environment profile isolation features from M25 or M38.

## Unchanged Behavior

Behaviors that must not change as a result of this change (leave blank if nothing is at risk):
- Existing agent-oriented SDK usage, `/api/v1/*` agent routes, `/services/*` service HTTP routes, and current JSON-RPC agent/tool methods remain compatible.
- This change does not require existing entrypoints to run through workflow runtime in order to preserve current behavior.
- This change does not redefine the spec-driven development loop as a business workflow engine.
