# Design: integration-testing

## Approach

Create a single `CrossLayerConsistencyTest` class that instantiates all three interface layers against the same SDK configuration and a shared stub LLM provider. Each test method runs the same logical scenario through each layer and asserts identical outcomes.

**Test architecture:**

1. **Shared fixtures** — a `StubLlmProvider` that returns deterministic responses (text response, then tool-call response, then text response), and a `StubTool` registered in the SDK builder. Both are reused across all three layers.

2. **Layer harnesses:**
   - **SDK layer**: call `SdkAgent.run()` directly
   - **JSON-RPC layer**: build framed stdin bytes, pump through `StdioTransport` → `JsonRpcDispatcher`, capture framed stdout, parse responses
   - **HTTP layer**: start embedded Tomcat with `HttpApiServlet` + `AuthFilter`, send requests via `HttpClient`, parse JSON responses

3. **Parameterized assertions** — each scenario is expressed as a set of expected outcomes (agent state, response content, tool invocation count). The same assertion lambda runs against results from all three layers.

4. **Test scenarios:**
   - Happy path: run agent → text response → STOPPED
   - Tool call loop: run agent → tool call → tool result → text response → STOPPED
   - Agent state query after run
   - Tools list parity
   - Error: run with invalid/no prompt
   - Error: HTTP unauthorized (401) vs JSON-RPC equivalent

## Key Decisions

- **One test class, not three**: A single `CrossLayerConsistencyTest` keeps cross-layer assertions co-located and makes it obvious when layers diverge.
- **Stub LLM, not real**: Deterministic stub responses make tests fast and repeatable. No network calls or API keys required.
- **@Isolated**: The test manages its own server and transport instances; JUnit isolation prevents port conflicts with existing E2E tests.
- **No changes to production code**: If a consistency gap is discovered, it will be filed as a separate fix, not patched during this change.

## Alternatives Considered

- **Three separate test classes per layer**: Rejected — the goal is to assert *consistency between layers*, which is best expressed by comparing results side-by-side in the same test method.
- **Contract testing with JSON schemas**: Over-engineered for the current project size. Structural assertions on response maps are sufficient.
- **Testcontainers with full process spawn**: Unnecessary complexity — in-process Tomcat and in-memory stdin/stdout are sufficient for testing the stack.
