# Design: jsonrpc-handlers

## Approach

Introduce a `JsonRpcDispatcher` class that implements `JsonRpcMessageHandler`. The dispatcher holds a reference to a `SpecDriven` SDK instance and a `JsonRpcTransport` for sending responses. On `onRequest`, it looks up the method name in an internal map and delegates to a typed handler method. On `onNotification`, it handles `$`-prefixed protocol notifications (e.g. `$/cancel`).

Agent sessions are tracked by request ID: when `agent/run` arrives, a new `SdkAgent` is created (or reused) and its run is submitted asynchronously. The dispatcher sends back a JSON-RPC response when the run completes, or a JSON-RPC error if it fails.

Events from agents are forwarded to the client as JSON-RPC notifications with method `event` and params containing the event data.

### Method table

| Method | Params | Response |
|---|---|---|
| `initialize` | `{configPath?: string, systemPrompt?: string}` | `{version: string, capabilities: object}` |
| `shutdown` | none | `null` |
| `agent/run` | `{prompt: string}` | `{output: string}` |
| `agent/stop` | `{agentId?: string}` | `null` |
| `agent/state` | `{agentId?: string}` | `{state: string}` |
| `tools/list` | none | `{tools: [{name, description, parameters}]}` |

### Notification table

| Method | Params | Direction |
|---|---|---|
| `$/cancel` | `{id: number\|string}` | client → server |
| `event` | `{type, source, metadata}` | server → client |

### Error mapping

| SDK Exception | JSON-RPC Error Code |
|---|---|
| `SdkConfigException` | `-32603` (internal error) |
| `SdkLlmException` | `-32603` (internal error) with retryable hint in data |
| `SdkPermissionException` | `-32600` (invalid request) |
| `SdkVaultException` | `-32603` (internal error) |
| `SdkToolException` | `-32602` (invalid params) |
| `JsonRpcProtocolException` | carries its own code |
| Unhandled | `-32603` (internal error) |

## Key Decisions

1. **One agent per `agent/run` call** — each call creates a fresh `SdkAgent` from the `SpecDriven` instance. No persistent agent IDs are required for the initial implementation. If `agentId` is provided in `agent/stop` or `agent/state`, it is accepted but currently ignored (only one in-flight run at a time).

2. **Synchronous dispatch, asynchronous execution** — the dispatcher processes the request synchronously to validate method/params, then submits `agent/run` asynchronously. The response is sent when the future completes.

3. **Event forwarding via `SdkEventListener`** — a listener registered on the SDK builder forwards all events as JSON-RPC notifications. This is set up during `initialize`.

4. **No batch request support** — consistent with M13 out-of-scope decision. The dispatcher handles one message per `onRequest` call.

5. **JSON serialization uses the same approach as codec** — no additional JSON library introduced. Handler results are converted to `Map<String, Object>` and wrapped in `JsonRpcResponse` for encoding by the existing `JsonRpcCodec`.

## Alternatives Considered

- **Persistent multi-agent sessions with IDs** — more complex, requires session store. Deferred; the single-agent-per-call model covers the CLI embedding use case first.
- **Annotation-based handler discovery** — over-engineered for 6 methods. A simple switch/map dispatch is sufficient and easier to verify.
- **Separate handler interface per method** — adds unnecessary abstraction. Typed private methods on the dispatcher are enough.
