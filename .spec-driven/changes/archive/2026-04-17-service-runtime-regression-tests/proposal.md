# service-runtime-regression-tests

## What

Strengthen automated regression coverage for the existing service runtime startup path,
service HTTP exposure, startup configuration validation, and structured failure
propagation.

This change will add or expand tests around the already-specified runtime entry
point and service HTTP namespace, focusing on high-risk deployment-path behavior
rather than introducing new product functionality.

## Why

The current roadmap phase prioritizes high-risk regression confidence before any
broader test-infrastructure work. The service runtime path is a thin but
business-critical integration surface: it combines service bootstrap, runtime
configuration governance, HTTP exposure, authentication boundaries, restart-like
startup behavior, and diagnosable failures.

Existing tests cover parts of this surface, but M39 explicitly calls for stronger
regression protection for service runtime, service HTTP exposure, startup
configuration, and error propagation. Tightening this coverage first reduces the
risk of deployment-path regressions while staying inside already-defined specs.

## Scope

In scope:
- Expand regression tests for `ServiceRuntimeLauncher` startup success and failure paths.
- Expand regression tests for packaged runtime service HTTP availability and auth boundaries.
- Expand regression tests for startup configuration validation and structured error reporting.
- Expand regression tests for CLI-visible runtime startup and failure output where that output is already part of the supported observable contract.
- Make only the smallest direct test-fixture or assertion-helper adjustments needed to support the new regression coverage.

Out of scope:
- Any new runtime feature, deployment feature, or service-management capability.
- Changes to public runtime, HTTP, SDK, or CLI behavior beyond clarifying and protecting already-specified behavior.
- Generalized test framework cleanup, repository-wide fixture standardization, or quality-gate redesign.
- Workflow recovery or cross-interface consistency work from the later M39 planned changes.

## Unchanged Behavior

Behaviors that must not change as a result of this change (leave blank if nothing is at risk):
- The supported Java service runtime entrypoint contract remains the same.
- The `/services/*` application-service HTTP contract remains the same.
- Existing `/api/v1/*` agent API behavior remains unchanged.
- Existing SDK and JSON-RPC behavior remains unchanged.
