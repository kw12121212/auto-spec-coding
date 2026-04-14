# Tasks: lealone-agent-adapter

## Implementation

- [ ] Add `LealoneAgentAdapter` under `org.specdriven.agent.interactive` implementing `InteractiveSession`
- [ ] Add or wire the smallest necessary Lealone execution dependency so the adapter can submit SQL/NL input and collect textual output without changing the public `InteractiveSession` contract
- [ ] Implement lifecycle handling for `start()`, `submit(String)`, `drainOutput()`, `close()`, and terminal execution failures
- [ ] Keep the adapter independent from `DefaultLoopDriver`, `QuestionRuntime`, and `QuestionDeliveryService` answer-routing behavior
- [ ] Add delta spec coverage for the Lealone-backed adapter behavior with implementation/test mapping frontmatter

## Testing

- [ ] Validation command: run `mvn -q -DskipTests compile`
- [ ] Unit test command: run `mvn -q -Dtest=LealoneAgentAdapterTest,InteractiveSessionTest test`
- [ ] Add unit tests for adapter lifecycle: start activates the session, close releases resources and remains idempotent, closed sessions reject later input
- [ ] Add unit tests for input/output behavior: submitted SQL/NL input reaches the Lealone execution dependency and returned output is available through ordered `drainOutput()`
- [ ] Add unit tests for failure behavior: execution failure transitions the adapter to `ERROR`, rejects later input, and still allows pending output to be drained

## Verification

- [ ] Verify the implementation does not modify `DefaultLoopDriver` pause/resume behavior or Question/Answer submission semantics
- [ ] Run `node /home/wx766/.agents/skills/roadmap-recommend/scripts/spec-driven.js verify lealone-agent-adapter` and fix safe artifact-format issues
