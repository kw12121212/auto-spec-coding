# service-http-exposure

## What

- Add the M36 application-level HTTP exposure contract for supported Lealone Service methods after declarative service application bootstrap.
- Define a stable `POST /services/{serviceName}/{methodName}` invocation path, positional JSON argument body, success response, and error response boundaries.
- Preserve the existing `/api/v1/*` agent management API behavior while adding a separate application-service HTTP namespace.

## Why

- `service-app-bootstrap` already defines how a supported `services.sql` application entry is loaded and started; M36 now needs a callable application surface for those bootstrapped services.
- A narrow HTTP exposure contract validates the runtime value of service application bootstrap before packaging and broader startup governance are added.
- Separating application-service routes from the existing agent REST API avoids accidental coupling between business service calls and management-plane agent operations.

## Scope

In scope:
- Specify the first supported application-service HTTP namespace as `POST /services/{serviceName}/{methodName}`.
- Specify request body shape for method invocation as positional JSON arguments using `{"args":[...]}`.
- Specify response behavior for successful service method calls and observable error behavior for unsupported methods, invalid arguments, service failures, and unknown routes.
- Specify that service HTTP exposure uses the existing HTTP authentication/filter boundary by default.
- Specify compatibility requirements so existing `/api/v1/*` agent API, SDK, and JSON-RPC behavior remains unchanged.

Out of scope:
- Runtime packaging, installer layout, production deployment workflow, or service process supervision.
- Schema bootstrap governance beyond the existing first bootstrap contract.
- A generic low-code router, UI generator, or non-Lealone service container abstraction.
- Supporting multiple request body formats, anonymous service invocation, or service discovery endpoints in this first exposure change.

## Unchanged Behavior

Behaviors that must not change as a result of this change (leave blank if nothing is at risk):
- Existing `/api/v1/*` routes keep their current route shape, auth behavior, request models, and error response semantics.
- Existing `SpecDriven` SDK and JSON-RPC entry points remain compatible.
- Existing `service-app-bootstrap` behavior remains startup-only and does not implicitly expose application services unless the supported HTTP exposure path is active.
