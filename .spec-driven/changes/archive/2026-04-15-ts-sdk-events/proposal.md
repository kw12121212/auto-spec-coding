# ts-sdk-events

## What

- Add a TypeScript SDK event subscription surface on top of the existing polling endpoint at `GET /api/v1/events`.
- Expose a typed, polling-backed helper that lets Node.js callers consume backend events continuously without hand-writing their own poll loop.
- Export the new event subscription types and helper from the existing `sdk/ts/` package entrypoints.

## Why

- `M21` is mostly complete, but its planned event capability is still missing while the backend polling endpoint and low-level `pollEvents()` client method already exist.
- Callers currently have to implement cursor management, polling cadence, and loop control themselves, which leaves the roadmap item only partially satisfied from an SDK-ergonomics perspective.
- Delivering the subscription layer before the remaining `ts-sdk-tests` change keeps milestone work in dependency order: finalize the public event API first, then add broader integration coverage against that settled surface.

## Scope

In scope:
- Define the observable TypeScript SDK event subscription behavior built on the existing HTTP polling route.
- Add the minimal TypeScript SDK API needed to start, iterate, and stop a polling-backed event subscription.
- Reuse the existing `pollEvents()` transport behavior and event payload shapes instead of introducing a new backend contract.
- Add TypeScript unit tests for the subscription helper behavior.

Out of scope:
- Adding or requiring a backend SSE endpoint.
- Changing the Java HTTP API event payload or authentication model.
- Replacing the existing low-level `pollEvents()` method.
- Real-backend integration test expansion beyond the focused SDK unit coverage needed for this change.

## Unchanged Behavior

Behaviors that must not change as a result of this change (leave blank if nothing is at risk):
- Existing `SpecDrivenClient.pollEvents()` request parameters, authentication behavior, and response normalization remain unchanged.
- Existing HTTP REST API routes and event payload shapes remain unchanged.
- Existing TypeScript SDK agent, tool, retry, and error handling behavior remain unchanged.
