# Questions: ts-sdk-tools

## Open

<!-- No open questions -->

## Resolved

- [x] Q: Does the TS SDK need to implement a server-side callback endpoint, or should it use a pull/polling model?
  Context: The Java `RemoteHttpTool` requires a `callbackUrl` and POSTs invocations to it. There is no backend polling endpoint for pending tool invocations.
  A: Callback/push model is required by the Java backend architecture. The TS SDK implements `ToolCallbackHandler` as an HTTP dispatch handler; the caller creates and binds the Node.js HTTP server and supplies the callback URL at registration time. (Confirmed by user 2026-04-15.)
