# Tasks: workflow-agent-human-bridge

## Implementation

- [x] Add `inputPrompt` field to `WorkflowStepResult` record; add `awaitingInput(String prompt)` factory, `isAwaitingInput()` predicate, and `inputPrompt()` accessor; preserve `success()` and `failure()` factory semantics
- [x] Add `WORKFLOW_PAUSED_FOR_INPUT` and `WORKFLOW_RESUMED` to `EventType` enum
- [x] Add optional `QuestionDeliveryService` parameter to `WorkflowRuntime` constructor; store it as a field
- [x] Subscribe `WorkflowRuntime` to `QUESTION_ANSWERED` events on `EventBus` at construction; on each event, check `sessionId` against pending futures map and complete matched future with answer content
- [x] In `advanceWorkflow()`, handle `isAwaitingInput()` step result: create `Question` (`sessionId = workflowId`, `category = PERMISSION_CONFIRMATION`, `deliveryMode = PAUSE_WAIT_HUMAN`, `question = inputPrompt()`), deliver via `QuestionDeliveryService`, store `CompletableFuture<String>` keyed by `workflowId`, publish `WORKFLOW_PAUSED_FOR_INPUT` event, transition to `WAITING_FOR_INPUT`, block virtual thread on future
- [x] Handle missing `QuestionDeliveryService`: if a step returns `awaitingInput` and no service is configured, immediately call `fail(record, "no question delivery surface configured")` instead of pausing
- [x] On future completion in `advanceWorkflow()`: transition to `RUNNING`, publish `WORKFLOW_RESUMED` event, inject `"humanInput"` → answer content into `stepInput`, continue step loop from next index

## Testing

- [x] Lint: `mvn checkstyle:check -pl .` (pre-existing violations; no new violations introduced by this change)
- [x] Unit tests: `mvn test -pl .` (1847 tests, 0 failures)
- [x] Test `WorkflowStepResult.awaitingInput()`: `isAwaitingInput() == true`, `isFailure() == false`, `inputPrompt()` returns the provided prompt
- [x] Test `WorkflowStepResult.success()`: `isAwaitingInput() == false`
- [x] Test `WorkflowStepResult.failure()`: `isAwaitingInput() == false`
- [x] Test: workflow with a step returning `awaitingInput` transitions to `WAITING_FOR_INPUT`
- [x] Test: `WORKFLOW_PAUSED_FOR_INPUT` event is published with correct `workflowId`, `questionId`, and `prompt` fields
- [x] Test: after pause, simulating a `QUESTION_ANSWERED` event with matching `sessionId` resumes the workflow to `RUNNING`
- [x] Test: `WORKFLOW_RESUMED` event is published after resume, containing `workflowId` and `questionId`
- [x] Test: resumed workflow injects `"humanInput"` answer content into the next step's input map
- [x] Test: resumed workflow that has no further steps transitions to `SUCCEEDED`
- [x] Test: `WorkflowRuntime` without a configured `QuestionDeliveryService` — step returning `awaitingInput` causes `FAILED` transition with a diagnostic message

## Verification

- [x] Run `mvn test` — all tests pass with no regressions in existing workflow or question tests
- [x] All delta-spec scenarios in `specs/workflow/workflow-runtime.md` are covered by at least one test
- [x] `WORKFLOW_PAUSED_FOR_INPUT` and `WORKFLOW_RESUMED` are present in `EventType` enum
- [x] No changes to `QuestionRuntime`, `QuestionDeliveryService`, or `InteractiveSession` public contracts
- [x] Run `/spec-driven-verify workflow-agent-human-bridge`
