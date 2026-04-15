# Questions: ts-sdk-client

## Open

<!-- No open questions -->

## Resolved

- [x] Q: Should the TypeScript package live in `sdk/ts/` as a standalone directory, or at the repo root / in a monorepo workspace?
  Context: Affects spec mapping paths and build commands.
  A: `sdk/ts/` standalone directory, mirroring the `go-sdk/` pattern.

- [x] Q: Should `ts-sdk-client` cover HTTP + JSON-RPC dual transport, or HTTP only?
  Context: JSON-RPC over stdin requires Node.js process-spawning; HTTP is the primary remote transport.
  A: HTTP transport only. JSON-RPC is deferred to a future change.
