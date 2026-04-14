# Design: jsonrpc-question-answer

## Approach

Add a new case `"question/answer"` to the existing switch-case dispatch in `JsonRpcDispatcher`. The handler method (`handleQuestionAnswer`) will:

1. Validate the SDK is initialized (same pattern as other handlers)
2. Extract and validate required parameters (`sessionId`, `questionId`, `approved`) from the request params map — return `-32602` (Invalid params) on missing parameters
3. Look up the waiting question from `QuestionRuntime` to determine its `deliveryMode`
4. If delivery mode is not `PAUSE_WAIT_HUMAN`, return an error indicating unsupported delivery mode
5. Construct an `Answer` object from the `approved` boolean with `HUMAN_INLINE` source
6. Submit via `QuestionDeliveryService.submitReply(sessionId, questionId, answer)`
7. Return `{"status": "accepted"}` on success
8. Map exceptions (question not found, expired, validation failures) to appropriate JSON-RPC error responses

The dispatcher will obtain `QuestionDeliveryService` from the SDK instance (through a new accessor method on `SpecDriven` if not already exposed, or through existing SDK internals).

## Key Decisions

1. **Use `QuestionDeliveryService.submitReply()` as the submission path** — This is the established public facade for external answer submission. It handles validation through `QuestionReplyCollector` and persists status changes via `QuestionStore`. Other external channels (Telegram, Discord) flow through this same path.

2. **Only support `PAUSE_WAIT_HUMAN` delivery mode** — `PAUSE_WAIT_HUMAN` is the natural fit for stdin/stdout JSON-RPC: the agent pauses, desktop shows a dialog, user responds, agent resumes. Supporting `PUSH_MOBILE_WAIT_HUMAN` would risk multi-channel race conditions (desktop and Telegram/Discord answering simultaneously). Future enhancement can add support with a simple deliveryMode check.

3. **Reject `PUSH_MOBILE_WAIT_HUMAN` with an explicit error** — Rather than silently failing or treating it the same as "question not found," the handler returns a distinct error so the desktop can differentiate "unsupported delivery mode" from "question expired."

4. **Answer construction from boolean** — The `approved` boolean is mapped to a full `Answer` object:
   - `source = HUMAN_INLINE` — identifies the answer came from a human via JSON-RPC
   - `decision = approved ? ANSWER_ACCEPTED : CANCELLED`
   - `confidence = 1.0` — human decisions are definitive
   - `content = "Approved"` / `"Rejected"`
   - `basisSummary = "Human inline response via JSON-RPC"`
   - `sourceRef = "json-rpc"`
   - `deliveryMode` — queried from the waiting question (must match for runtime validation)

5. **Error code mapping** — Follows existing dispatcher patterns:
   - SDK not initialized → `-32600` (Invalid Request)
   - Missing parameters → `-32602` (Invalid Params)
   - Question not found / expired / unsupported delivery mode → `-32603` (Internal error) with descriptive message

## Alternatives Considered

1. **Direct `QuestionRuntime.submitAnswer()` call** — Would bypass the validation and persistence in `QuestionDeliveryService`. Rejected because it would duplicate validation logic and miss the status persistence step.

2. **Support all delivery modes** — Could allow desktop to answer any waiting question. Rejected due to multi-channel race condition risk for `PUSH_MOBILE_WAIT_HUMAN` questions.

3. **New SDK-level convenience method** — A `SpecDriven.submitQuestionAnswer(sessionId, questionId, boolean)` method that encapsulates the Answer construction. Viable but adds API surface for a single use case. The dispatcher can construct the Answer directly, keeping the SDK API minimal.
