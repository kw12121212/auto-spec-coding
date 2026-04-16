# Design: workflow-agent-human-bridge

## Approach

### 1. Extend `WorkflowStepResult` with an awaiting-input variant

`WorkflowStepResult` gains a third nullable field `inputPrompt`. A new factory method
`awaitingInput(String prompt)` sets it; `isAwaitingInput()` returns `true` when `inputPrompt` is
non-null and `failureReason` is null. Existing `success()` and `failure()` factories are
unaffected — they set `inputPrompt = null`.

### 2. Wire `QuestionDeliveryService` into `WorkflowRuntime`

`WorkflowRuntime`'s constructor gains an optional `QuestionDeliveryService` parameter. When absent
(null), any step returning `awaitingInput` causes an immediate `FAILED` transition — no silent
hang. This keeps existing tests and callers that don't need human-in-loop working without change.

### 3. Suspend/resume via `CompletableFuture` and `EventBus` subscription

When `advanceWorkflow()` encounters an `isAwaitingInput()` result:
1. Creates a `Question` with `sessionId = workflowId`, `category = PERMISSION_CONFIRMATION`,
   `deliveryMode = PAUSE_WAIT_HUMAN`
2. Delivers via `QuestionDeliveryService`
3. Stores a `CompletableFuture<String>` (keyed by `workflowId`) in `WorkflowRuntime`
4. Publishes `WORKFLOW_PAUSED_FOR_INPUT` (includes `workflowId`, `questionId`, `prompt`)
5. Transitions to `WAITING_FOR_INPUT`
6. Blocks the virtual thread on the future (configurable timeout; timeout-caused failure deferred
   to `workflow-recovery-audit`)

`WorkflowRuntime` subscribes to `QUESTION_ANSWERED` events on startup. On each event it checks
`sessionId == workflowId` of a pending future; if matched, completes the future with the answer
content from event metadata.

On future completion:
1. Transitions back to `RUNNING` and publishes `WORKFLOW_RESUMED`
2. Injects `"humanInput"` → answer content into the running step-input map
3. Continues the step loop from the next step index

### 4. New event types

`EventType` gains `WORKFLOW_PAUSED_FOR_INPUT` and `WORKFLOW_RESUMED`.

## Key Decisions

| Decision | Rationale |
|---|---|
| `EventBus` subscription for resume trigger | Avoids direct coupling to `QuestionRuntime` internals; both `QuestionRuntime` and mobile reply paths already emit `QUESTION_ANSWERED` |
| `sessionId = workflowId` for correlation | Workflow ID is stable, unique, and non-blank; no new identifier needed |
| `QuestionDeliveryService` optional at construction | Keeps existing callers working; fail-safe fallback (immediate FAILED) prevents silent hangs |
| Inject answer as `"humanInput"` key | Predictable, avoids overwriting other step output keys |
| Blocking virtual thread | Safe with `Executors.newVirtualThreadPerTaskExecutor()`; avoids separate state machine overhead |

## Alternatives Considered

- **Direct `QuestionRuntime.registerAnswerListener()`** — tighter coupling, would require changing
  the `QuestionRuntime` contract (spec violation); rejected.
- **Separate `WorkflowHumanBridge` class** — adds indirection without justification at this scope;
  rejected.
- **Using `InteractiveSession` directly as the pause mechanism** — bypasses `QuestionDeliveryService`
  and doesn't support mobile delivery; rejected. `InteractiveSession` remains a valid *channel
  adapter* to submit answers, not the pause mechanism itself.
- **Multi-step await (pause multiple times)** — deferred to a future change; one pause per
  execution is sufficient for the bridge requirement.
