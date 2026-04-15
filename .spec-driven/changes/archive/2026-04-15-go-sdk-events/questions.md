# Questions: go-sdk-events

## Open

<!-- No open questions -->

## Resolved

- [x] Q: Should `go-sdk-events` include the minimal Java HTTP event subscription endpoint, or should it only define the Go SDK client against an assumed future endpoint?
  Context: The current HTTP API has no event subscription route, so a usable Go SDK event subscriber needs a backend event source.
  A: Include a minimal authenticated polling endpoint in this change, and leave SSE out of scope.
