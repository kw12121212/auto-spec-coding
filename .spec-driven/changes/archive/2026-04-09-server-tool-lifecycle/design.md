# Design: server-tool-lifecycle

## Approach

1. **ServerTool interface** — a new interface extending `BackgroundTool` with a single method `getReadyProbe()` returning a `ReadyProbe`. This keeps the contract minimal: any tool that needs readiness probing implements `ServerTool` and declares its probe configuration.

2. **ReadyProbe record** — a declarative descriptor with fields: `type` (enum: TCP, HTTP), `host` (String, default "localhost"), `port` (int), `path` (String, optional, for HTTP probes), `expectedStatus` (int, optional, for HTTP probes, default 200), `timeout` (Duration, default 30s), `retryInterval` (Duration, default 1s), `maxRetries` (int, default 30). The probe target (port) is declared upfront by the tool, not parsed from stdout.

3. **ProbeStrategy interface** — a single-method interface `boolean probe(ReadyProbe probe)`. Built-in implementations:
   - `TcpProbeStrategy` — attempts a TCP connect to host:port, returns true if connection succeeds
   - `HttpProbeStrategy` — sends an HTTP GET to the given URL/path, returns true if the response status matches `expectedStatus`

4. **ProcessManager extensions** — add two new methods:
   - `waitForReady(String processId, Duration timeout)` — looks up the process, retrieves the associated `ReadyProbe` (if any), and runs the probe strategy in a retry loop until timeout
   - `cleanup(String processId)` — stops the process and releases any server-specific resources (currently equivalent to `stop()` but reserved for future resource cleanup like temp file removal)

5. **ProcessManager probe storage** — `register()` is extended to accept an optional `ReadyProbe`. A `registerWithProbe(Process, String, String, ReadyProbe)` overload is added. The existing `register()` delegates to it with a null probe.

6. **New event types** — `SERVER_TOOL_READY` and `SERVER_TOOL_FAILED` (probe timeout) with metadata: `processId`, `toolName`, `probeType`.

## Key Decisions

- **Declarative probe over callback-based**: Tools declare their probe config upfront via `ReadyProbe`, rather than implementing probe logic themselves. This keeps tools simple and probe strategies pluggable.
- **Port declared upfront, not parsed from stdout**: Simpler and more reliable. Port parsing from stdout can be added later as a separate concern.
- **Probe strategy as interface, not enum dispatch**: Allows custom strategies via SPI in the future without modifying existing code.
- **`cleanup()` as explicit method**: Even though it currently delegates to `stop()`, having a separate method reserves the extension point for future resource cleanup (temp files, port release, etc.).

## Alternatives Considered

- **Marker interface + separate ServerToolRegistry**: Rejected — would require a parallel tracking mechanism when ProcessManager already tracks all processes.
- **Probe config in ToolInput parameters**: Rejected — probe config is a tool-level property, not per-invocation. Putting it in ToolInput would require every caller to pass it.
- **Auto-detect port from stdout regex**: Rejected for this change — adds complexity (regex patterns, timing issues). Can be added as a future enhancement.
