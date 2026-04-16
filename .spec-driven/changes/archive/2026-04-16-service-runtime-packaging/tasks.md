# Tasks: service-runtime-packaging

## Implementation

- [x] Update the M36 runtime packaging delta specs for the Java CLI runtime entrypoint, startup sequencing, structured output, and compatibility boundaries.
- [x] Add the Java CLI service runtime command or entrypoint for launching a supported `services.sql` service application.
- [x] Wire the runtime entrypoint to assemble a single SDK/platform runtime, apply `services.sql` bootstrap, and activate the existing service HTTP namespace.
- [x] Add structured startup success and failure output for missing input, unsupported bootstrap input, runtime bootstrap failure, and HTTP startup failure.
- [x] Document development and packaged-runtime startup commands in repository-facing runtime materials.

## Testing

- [x] Run validation command `mvnd -q -DskipTests compile`.
- [x] Run unit test command `mvnd test -q -Dsurefire.useFile=false`.
- [x] Add focused JUnit tests for CLI argument validation, missing `services.sql`, unsupported bootstrap input failure, successful startup sequencing, structured output, service route availability, and `/api/v1/*` compatibility.

## Verification

- [x] Verify implementation remains limited to runtime packaging and does not add production install, repair, or service-manager behavior.
- [x] Verify runtime startup reuses the existing `services.sql` bootstrap and service HTTP exposure contracts rather than defining parallel behavior.
- [x] Verify delta specs describe observable behavior and avoid implementation-only requirements.
- [x] Run spec-driven verification for `service-runtime-packaging`.
