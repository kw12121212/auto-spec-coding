# Tasks: service-http-exposure

## Implementation

- [x] Update the M36 HTTP delta spec for application-service route namespace, request body, response, and error behavior.
- [x] Define the compatibility boundary between `POST /services/{serviceName}/{methodName}` and existing `/api/v1/*` agent API routes.
- [x] Implement service method invocation routing for supported Lealone Service methods through the HTTP layer.
- [x] Add request/response JSON handling for positional `args` service invocation bodies.
- [x] Ensure service HTTP invocation uses the existing HTTP authentication/filter boundary by default.

## Testing

- [x] Run validation command `mvnd -q -DskipTests compile`.
- [x] Run unit test command `mvnd test -q -Dsurefire.useFile=false`.
- [x] Add focused JUnit tests for service invocation success, invalid argument body, unknown service or method, service failure response, authentication boundary, and `/api/v1/*` route compatibility.

## Verification

- [x] Verify the change remains limited to service HTTP exposure and does not add runtime packaging or production install behavior.
- [x] Verify the exposed service namespace does not alter existing `/api/v1/*` behavior.
- [x] Verify the delta spec uses observable behavior and avoids implementation-only requirements.
