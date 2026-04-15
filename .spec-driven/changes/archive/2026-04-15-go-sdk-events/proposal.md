# go-sdk-events

## What

Add event subscription support for the Go SDK by introducing a bounded HTTP event polling surface on the Java backend and a Go SDK events facade that can poll or continuously subscribe to that surface.

The change will make backend events visible to Go callers as typed Go values, preserve event ordering through a server-assigned cursor, and let callers use normal Go contexts to stop polling or subscription work.

## Why

M20 aims to provide a complete Go SDK over the Java backend HTTP REST API. The client, agent, and tools layers already exist; event subscription is the remaining functional SDK capability before the milestone can move to integration-test closure.

The current backend has an internal `EventBus`, but no HTTP route that a remote Go process can use to observe events. A minimal polling route gives the Go SDK a concrete, testable event source without expanding this change into SSE connection management.

## Scope

In scope:

- Add an authenticated HTTP event polling endpoint under `/api/v1/events`.
- Return retained events in stable ascending cursor order.
- Support cursor-based polling with optional event-type filtering and bounded result limits.
- Add Go SDK event models matching the HTTP event payload.
- Add low-level client polling and a high-level events facade.
- Add a Go polling subscriber that repeatedly polls, advances its cursor, invokes caller callbacks, and stops on context cancellation.
- Add Java unit tests for HTTP event route behavior.
- Add Go unit tests for event polling, subscription cursor advancement, auth, filtering, API errors, and context cancellation.

Out of scope:

- Server-Sent Events or other long-lived streaming transport.
- Persistent event storage backed by `AuditLogStore` or Lealone DB.
- Replay of events produced before the HTTP event endpoint begins observing the SDK event bus.
- TypeScript SDK event support.
- Cross-language integration tests against a live Java backend; those remain in `go-sdk-tests`.

## Unchanged Behavior

Behaviors that must not change as a result of this change (leave blank if nothing is at risk):

- Existing HTTP routes for health, tools, agent run/stop/state, callbacks, and delivery status keep their request and response contracts.
- Existing Go SDK client, agent, and tools APIs remain backward compatible.
- Existing in-process SDK event listener behavior remains unchanged.
- Existing event serialization constraints for `Event.toJson()` and `Event.fromJson()` remain unchanged.
