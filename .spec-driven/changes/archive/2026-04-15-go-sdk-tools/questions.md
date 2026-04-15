# Questions: go-sdk-tools

## Open

<!-- No open questions -->

## Resolved

- [x] Q: Should `go-sdk-tools` add backend HTTP API support for remote tool registration/invocation, or should it be limited to the existing `/api/v1/tools` listing contract?
  Context: The current HTTP REST API only lists tools. The roadmap item requires tool registration and invocation wrapping for the Go SDK, so proposal scope depends on whether this change may introduce a minimal cross-layer contract.
  A: Add the minimal backend HTTP API support needed for callback-backed remote tool registration and invocation from Go.
