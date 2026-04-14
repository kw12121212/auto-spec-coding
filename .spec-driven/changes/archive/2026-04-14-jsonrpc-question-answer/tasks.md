# Tasks: jsonrpc-question-answer

## Implementation

- [x] Add `case "question/answer"` to `JsonRpcDispatcher` switch-case routing
- [x] Implement `handleQuestionAnswer(JsonRpcRequest)` private method with parameter validation, Answer construction, and submission via `QuestionDeliveryService.submitReply()`
- [x] Add `"question/answer"` to the `capabilities.methods` list in `handleInitialize` response
- [x] Ensure the dispatcher can access `QuestionDeliveryService` from the SDK instance (add accessor if needed)

## Testing

- [x] Run `mvn compile -pl .` to validate compilation
- [x] Add unit tests in `JsonRpcDispatcherTest` for: approve, reject, SDK not initialized, missing params, question not found/expired, unsupported delivery mode
- [x] Run `mvn test -pl . -Dtest=JsonRpcDispatcherTest` to verify all tests pass

## Verification

- [x] Verify implementation matches proposal — all scenarios in delta spec are covered
- [x] Verify `initialize` response includes `"question/answer"` in capabilities
- [x] Verify `PAUSE_WAIT_HUMAN` questions are answered and agent resumes execution
