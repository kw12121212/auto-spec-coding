# Questions: server-tool-lifecycle

## Open

<!-- No open questions -->

## Resolved

- [x] Q: How does a tool declare itself as "server-class"?
  Context: No existing mechanism for tools to signal they need readiness probing.
  A: `ServerTool` interface extending `BackgroundTool` with `getReadyProbe()` method.

- [x] Q: What default readiness probe strategies should be supported?
  Context: Milestone says health check strategy SHOULD be configurable.
  A: Two built-in: `TcpProbeStrategy` (TCP connect) and `HttpProbeStrategy` (HTTP GET + status code). Custom via `ProbeStrategy` interface.

- [x] Q: Should port discovery be automatic or declared upfront?
  Context: Some servers print port to stdout, others use fixed ports.
  A: Declared upfront in `ReadyProbe`. Port parsing from stdout is out of scope.
