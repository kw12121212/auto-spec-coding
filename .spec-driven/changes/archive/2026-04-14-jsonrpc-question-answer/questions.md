# Questions: jsonrpc-question-answer

## Open

<!-- No open questions — all decisions resolved during brainstorm -->

## Resolved

- [x] Q: Should `question/answer` support `PUSH_MOBILE_WAIT_HUMAN` delivery mode?
  Context: Desktop receives all QUESTION_CREATED events regardless of delivery mode.
  A: No. Only `PAUSE_WAIT_HUMAN` is supported to avoid multi-channel race conditions. `PUSH_MOBILE_WAIT_HUMAN` questions answered via JSON-RPC would compete with Telegram/Discord replies.

- [x] Q: Which submission path should the handler use — `QuestionRuntime.submitAnswer()` or `QuestionDeliveryService.submitReply()`?
  Context: Both paths can submit answers, but have different validation/persistence behavior.
  A: Use `QuestionDeliveryService.submitReply()`. It provides the complete validation chain (via `QuestionReplyCollector`) and handles status persistence (via `QuestionStore`), consistent with how other external channels submit answers.

- [x] Q: What `QuestionDecision` should a rejected answer use?
  Context: The `approved` boolean maps to an `Answer` which requires a `QuestionDecision`.
  A: `CANCELLED` for rejection (`approved = false`), `ANSWER_ACCEPTED` for approval (`approved = true`).
