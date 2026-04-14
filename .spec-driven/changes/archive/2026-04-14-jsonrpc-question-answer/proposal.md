# jsonrpc-question-answer

## What

Add a `question/answer` JSON-RPC method to `JsonRpcDispatcher` so that desktop clients connected via stdin/stdout can submit human answers to waiting questions (approve/reject) back to the agent.

## Why

Desktop clients receive `QUESTION_CREATED` event notifications when the agent needs human confirmation for dangerous operations. However, the agent currently has no JSON-RPC method to receive the user's answer back. Existing answer submission channels (HTTP callback endpoints, internal SDK calls) are not accessible to desktop clients communicating over stdin/stdout JSON-RPC. This creates a one-way communication gap: the desktop can see questions but cannot respond to them.

## Scope

**In scope:**
- New `question/answer` JSON-RPC method in `JsonRpcDispatcher`
- Request parameters: `sessionId`, `questionId`, `approved` (boolean)
- Success response: `{"status": "accepted"}`
- Error handling: SDK not initialized, missing parameters, question not found/expired, unsupported delivery mode
- Answer construction: convert `approved` boolean to `Answer` object with `HUMAN_INLINE` source
- Only support `PAUSE_WAIT_HUMAN` delivery mode questions
- Update `initialize` response `capabilities.methods` to include `"question/answer"`

**Out of scope:**
- Supporting `PUSH_MOBILE_WAIT_HUMAN` delivery mode (multi-channel race condition risk)
- Modifying Question/Answer lifecycle or routing semantics
- Changing question event structure or metadata
- New event types or delivery channels

## Unchanged Behavior

- Existing `QuestionRuntime.submitAnswer()` validation and queue mechanics
- Existing `QuestionDeliveryService.submitReply()` validation chain
- Question lifecycle transitions (OPEN → WAITING_FOR_ANSWER → ANSWERED/EXPIRED)
- Event emission for QUESTION_CREATED, QUESTION_ANSWERED, QUESTION_EXPIRED
- Agent pause/resume behavior for PAUSE_WAIT_HUMAN questions
- All other JSON-RPC methods (initialize, shutdown, agent/run, agent/stop, agent/state, tools/list, $/cancel)
