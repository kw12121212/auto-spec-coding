# Design: service-runtime-packaging

## Approach

- Treat runtime packaging as a thin Java entrypoint and documentation layer over the completed M36 bootstrap and HTTP exposure contracts.
- Define the first supported service application runtime command as a Java CLI path that accepts a readable `services.sql` file and minimal runtime settings.
- Require the runtime entrypoint to assemble one `SpecDriven`/`LealonePlatform` runtime, run the existing supported service bootstrap against that runtime, start the runtime, and expose the existing application-service HTTP namespace.
- Keep startup output observable and stable: callers can tell whether startup succeeded, which `services.sql` path was used, which HTTP bind settings were selected, and why startup failed.
- Keep the contract narrow enough that `service-schema-bootstrap-governance` can later add stricter policy without changing the basic runtime entrypoint.

## Key Decisions

- Use Java CLI as the primary service runtime entrypoint.
  Rationale: the repository is a Maven/JDK project and already has Java CLI test coverage; this avoids making shell scripts or external launchers the primary supported contract.
- Build on `services.sql` rather than adding a second application manifest.
  Rationale: `service-app-bootstrap` intentionally made `services.sql` the first supported declarative application entry.
- Reuse the existing service HTTP namespace.
  Rationale: `service-http-exposure` already defines request, response, error, and auth behavior for application-service calls.
- Keep production install and service-manager behavior out of scope.
  Rationale: M25 owns production install/repair, while this change only defines how the Java runtime package starts a supported service application.
- Return structured startup output.
  Rationale: operators and tests need stable observable status instead of inferring success from log text or process lifetime alone.

## Alternatives Considered

- Make a shell script the primary runtime contract.
  Rejected because scripts may be useful wrappers, but the first supported behavior should be testable through the Java/Maven runtime surface.
- Add a new declarative runtime manifest next to `services.sql`.
  Rejected because M36 has not yet introduced governance for multiple startup formats, and the completed bootstrap contract is SQL-centered.
- Combine runtime packaging with schema bootstrap governance.
  Rejected because governance is a separate planned M36 change and would make this change too broad.
- Wait for M25 production install before defining this runtime entrypoint.
  Rejected because M36 can define repo-local development and packaged Java startup behavior without taking over remote production install or repair.
