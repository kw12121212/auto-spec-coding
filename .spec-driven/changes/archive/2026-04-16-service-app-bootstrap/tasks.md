# Tasks: service-app-bootstrap

## Implementation

- [x] Update the M36 delta specs for platform and SDK bootstrap behavior around a supported `services.sql` application entry.
- [x] Define the observable bootstrap contract, including supported input shape, idempotent repeated startup, and explicit unsupported-input failure boundaries.
- [x] Specify coexistence boundaries so application bootstrap does not change existing SDK, JSON-RPC, or `/api/v1/*` agent API behavior.

## Testing

- [x] Run validation command `mvn -q -DskipTests compile`
- [x] Run unit test command `mvn test -q -Dsurefire.useFile=false`

## Verification

- [x] Verify the proposal stays within `service-app-bootstrap` scope and does not predefine `service-http-exposure` or `service-runtime-packaging` behavior.
- [x] Verify the delta specs use the existing main spec paths and platform-backed repository evidence.
- [x] Verify the bootstrap contract preserves compatibility for existing SDK, JSON-RPC, and `/api/v1/*` agent API entry paths.
