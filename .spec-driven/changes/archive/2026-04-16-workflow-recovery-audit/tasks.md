# Tasks: workflow-recovery-audit

## Implementation

- [x] Add a workflow runtime delta spec for checkpoint persistence, runtime-driven recovery of running and waiting workflows, automatic retry of retryable step failures, and diagnosable failure results.
- [x] Update the workflow step composition delta to define a retryable step-failure result and the rule that retries occur on the same step boundary before any later step executes.
- [x] Update the event system delta to define the new workflow recovery audit events and their required metadata.
- [x] Verify the proposal keeps the first recovery change runtime-driven and does not add a new caller-triggered SDK/API recovery operation.

## Testing

- [x] Run validation command `mvn -q -DskipBuiltinToolsDownload=true -DskipTests compile`
- [x] Run focused unit test command `mvn -q -DskipBuiltinToolsDownload=true -Dtest=WorkflowRuntimeTest,WorkflowStepCompositionTest,WorkflowAgentHumanBridgeTest,EventSystemTest test`
- [x] Run full unit test command `mvn -q -DskipBuiltinToolsDownload=true test`

## Verification

- [x] Verify persisted recovery keeps the same `workflowId` and resumes from the correct unfinished step boundary instead of re-running completed steps.
- [x] Verify waiting workflows retain pending question correlation across supported recovery and resume through the existing answer path.
- [x] Verify retryable failures never allow later steps to run early and that final failure diagnostics expose the failed step, stable reason, and retry exhaustion when applicable.
- [x] Verify each delta spec mirrors the main spec path exactly and only uses mapping paths that are supported by current repository evidence.
