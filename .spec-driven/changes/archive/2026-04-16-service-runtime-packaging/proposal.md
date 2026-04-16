# service-runtime-packaging

## What

- Add the M36 minimum runtime packaging contract for launching a Lealone service application from the repository's Java runtime surface.
- Define a primary Java CLI entrypoint that accepts a supported `services.sql` path plus minimal runtime configuration, starts the assembled runtime, bootstraps the service application, and makes the existing service HTTP namespace available.
- Document the development and packaged-runtime startup contract so operators do not need to manually reconstruct Lealone platform, bootstrap, and HTTP exposure initialization order.

## Why

- `service-app-bootstrap` already defines supported `services.sql` bootstrap behavior, and `service-http-exposure` already defines the application-service HTTP invocation namespace.
- M36 now needs a narrow packaging and launch contract that ties those completed pieces into a runnable service application experience.
- Locking the runtime entrypoint before broader schema governance keeps this change focused on startup usability while leaving policy tightening to `service-schema-bootstrap-governance`.

## Scope

In scope:
- Define the first supported Java CLI runtime entrypoint for service application startup.
- Specify required startup inputs: readable `services.sql`, minimal runtime config, and HTTP bind settings needed to expose service routes.
- Specify startup sequencing: assemble SDK/platform runtime, apply supported service bootstrap, start the platform/runtime, and expose application-service HTTP routes.
- Specify structured startup success and failure output for missing files, unsupported bootstrap input, HTTP startup failure, and runtime bootstrap failure.
- Specify development and packaged-runtime invocation documentation.
- Preserve existing SDK, JSON-RPC, `/api/v1/*` agent API, `services.sql` bootstrap, and service HTTP invocation behavior.

Out of scope:
- Production install, remote repair, process supervision, service-manager integration, or one-click deployment workflow.
- New service route shapes beyond the existing `POST /services/{serviceName}/{methodName}` contract.
- Broader schema/service governance beyond the current supported bootstrap input boundary.
- Non-Java launchers as first-class runtime contracts.
- Multi-node, clustered, blue/green, or rolling runtime packaging.

## Unchanged Behavior

Behaviors that must not change as a result of this change (leave blank if nothing is at risk):
- Existing `SpecDriven` SDK agent usage remains compatible.
- Existing JSON-RPC behavior remains compatible.
- Existing `/api/v1/*` agent management API route shape, auth behavior, and error semantics remain compatible.
- Existing `services.sql` bootstrap behavior remains the source of truth for supported declarative startup input.
- Existing `POST /services/{serviceName}/{methodName}` service HTTP invocation behavior remains the source of truth for application-service calls.
