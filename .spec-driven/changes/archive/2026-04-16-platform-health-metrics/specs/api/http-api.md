---
mapping:
  implementation:
    - src/main/java/org/specdriven/agent/http/HttpApiServlet.java
    - src/main/java/org/specdriven/agent/http/HttpJsonCodec.java
    - src/main/java/org/specdriven/agent/http/PlatformHealthResponse.java
  tests:
    - src/test/java/org/specdriven/agent/http/HttpApiServletTest.java
---

## ADDED Requirements

### Requirement: Platform health route

The HTTP API MUST provide a `GET /platform/health` route that returns platform subsystem health when a `LealonePlatform` is assembled into the SDK.

#### Scenario: Platform health returns aggregated status
- GIVEN an `HttpApiServlet` backed by a `SpecDriven` instance that has an assembled `LealonePlatform`
- WHEN a GET request with `pathInfo="/platform/health"` is received
- THEN a `PlatformHealthResponse` MUST be returned with HTTP 200
- AND the response MUST include `overallStatus`, a `subsystems` array (each with `name`, `status`, and optional `message`), and `probedAt`

#### Scenario: Platform health returns 404 when no platform is assembled
- GIVEN an `HttpApiServlet` backed by a `SpecDriven` instance built without `buildPlatform()`
- WHEN a GET request with `pathInfo="/platform/health"` is received
- THEN an `ErrorResponse` with `status=404` and `error="not_found"` MUST be returned

### Requirement: PlatformHealthResponse type

The system MUST provide an immutable `PlatformHealthResponse` record in `org.specdriven.agent.http` for JSON serialization of platform health results.

#### Scenario: Response carries subsystem details
- GIVEN a `PlatformHealth` with two subsystems
- WHEN a `PlatformHealthResponse` is constructed from it
- THEN `overallStatus()` MUST match the `PlatformHealth` overall status name
- AND `subsystems()` MUST contain one entry per `SubsystemHealth` with matching name, status, and message

## MODIFIED Requirements

### Requirement: Route dispatching

Previously: The servlet dispatches to health, tools, events, agent/run, agent/stop, agent/state, and unknown routes.

The servlet MUST additionally dispatch `GET /platform/health` to the platform health handler.

## MODIFIED Requirements

### Requirement: HttpJsonCodec encoding

Previously: `HttpJsonCodec` encodes `RunAgentResponse`, `HealthResponse`, `ErrorResponse`, and `ToolsListResponse`.

`HttpJsonCodec` MUST additionally support `encode(PlatformHealthResponse)`.
