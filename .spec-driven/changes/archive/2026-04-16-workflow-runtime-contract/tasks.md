# Tasks: workflow-runtime-contract

## Implementation

- [x] Add a new workflow runtime delta spec defining declaration parity, workflow instance identity, lifecycle states, start behavior, state query, and result retrieval.
- [x] Update the SDK public API delta to define workflow declaration registration and workflow instance operations without changing existing agent-oriented SDK behavior.
- [x] Update the HTTP REST and JSON-RPC deltas to define workflow start, state, and result operations with stable compatibility boundaries.
- [x] Update the event-system delta to define workflow lifecycle event names and minimum audit metadata.
- [x] Ensure the main spec index will gain an entry for the new workflow runtime spec when the change is merged.

## Testing

- [x] Run validation command `mvn -q -DskipTests compile`
- [x] Run unit test command `mvn -q test`

## Verification

- [x] Verify the first workflow change remains limited to contract definition and does not expand into workflow step composition, human bridge behavior, or recovery and retry semantics.
- [x] Verify every delta spec mirrors the main spec path and uses repository-backed mapping paths only when they are knowable from the current repository.
- [x] Verify the proposal preserves existing SDK agent flows, `/api/v1/*` agent routes, `/services/*` service routes, and current JSON-RPC agent and tool behavior unless callers explicitly use workflow features.
