# workflow-agent-human-bridge

## What

Wire the existing `QuestionDeliveryService`, `QuestionRuntime`, and `InteractiveSession`
capabilities into the workflow pause → question → resume chain.

A workflow step executor that returns an "awaiting input" signal causes `WorkflowRuntime` to:
1. Create and deliver a `Question` via `QuestionDeliveryService` (using the workflow instance ID as
   the correlation session ID)
2. Transition the workflow to `WAITING_FOR_INPUT`
3. Suspend execution of the virtual-thread workflow loop
4. Resume when a matching answer arrives via the existing `QUESTION_ANSWERED` event on the `EventBus`
5. Inject the answer content as `"humanInput"` in the next step's input context and continue execution

`WorkflowStepResult` gains an `awaitingInput(prompt)` factory variant so that step executors can
signal the need for human input without requiring the runtime to understand step internals.

Two new audit event types are added: `WORKFLOW_PAUSED_FOR_INPUT` and `WORKFLOW_RESUMED`.

## Why

The `WAITING_FOR_INPUT` workflow status was declared in `workflow-runtime-contract` but currently
has no real entry or exit path — it is only reachable through a test-control flag in the runtime.
M37's done-criteria explicitly require that human-in-loop capabilities from M22 (question
resolution), M23 (mobile delivery), and M29 (interactive human-in-loop) be bridged into the
workflow pause/resume chain. All dependency milestones are complete and their contracts are stable.

## Scope

**In scope:**
- New `WorkflowStepResult.awaitingInput(String prompt)` factory, `isAwaitingInput()` predicate,
  and `inputPrompt()` accessor
- `WorkflowRuntime` optionally accepts `QuestionDeliveryService` at construction
- `WorkflowRuntime` subscribes to `QUESTION_ANSWERED` events on `EventBus`; when matched by
  `sessionId == workflowId`, completes the pending workflow resume future
- `advanceWorkflow()` handles `isAwaitingInput()` step results: create `Question`, deliver,
  transition to `WAITING_FOR_INPUT`, suspend virtual thread, resume on answer
- Answer content is injected as `"humanInput"` in the next step's input context
- `WORKFLOW_PAUSED_FOR_INPUT` and `WORKFLOW_RESUMED` added to `EventType` enum
- Integration test demonstrating inline resume via `InteractiveCommandHandler` / `CommandParsingSession`

**Out of scope:**
- Modifying `QuestionRuntime`, `QuestionDeliveryService`, `InteractiveSession`, or
  `InteractiveCommandHandler` contracts
- Adding new delivery channels (Telegram/Discord) for workflow questions — existing registered
  channels are reused
- Timeout-based recovery of paused workflows (deferred to `workflow-recovery-audit`)
- Multi-step human input sequences within a single pause

## Unchanged Behavior

- All existing `QuestionRuntime`, `QuestionDeliveryService`, `InteractiveSession`,
  `InteractiveCommandHandler`, and `CommandParsingSession` behavior
- All existing `WorkflowRuntime` behavior for workflows whose steps never return `awaitingInput`
- `WorkflowStepResult.success()` and `WorkflowStepResult.failure()` semantics are unchanged
