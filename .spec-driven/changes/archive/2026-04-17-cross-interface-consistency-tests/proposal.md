# cross-interface-consistency-tests

## What

Strengthen automated regression coverage for cross-interface consistency across
the existing native Java SDK, HTTP REST API, and JSON-RPC entry surfaces.

This change will expand or refine tests around already-specified observable
behavior so that the same logical operations produce compatible externally
visible outcomes across the three supported interface layers, without adding new
product functionality.

## Why

The current roadmap phase prioritizes high-risk regression confidence before any
broader test-infrastructure work. The repository already exposes three
release-facing entry surfaces, and M39 explicitly calls out cross-interface
consistency as the remaining planned regression-hardening gap.

There is already a starting point in
`src/test/java/org/specdriven/agent/integration/CrossLayerConsistencyTest.java`,
but the roadmap still treats this as unfinished planned work. Completing it now
reduces the risk that the same supported capability drifts across SDK, HTTP, and
JSON-RPC while keeping scope inside current observable contracts.

## Scope

In scope:
- Review existing cross-layer, HTTP, JSON-RPC, and SDK tests against the current
  observable specs for shared public behaviors.
- Expand consistency regression tests for shared operations such as supported
  lifecycle flows, tool discovery, and error semantics where parity coverage is
  currently missing.
- Clarify and enforce which behaviors are expected to be identical across all
  three surfaces versus where protocol-specific representation differences are
  acceptable.
- Make only the smallest local test-fixture or assertion-helper adjustments
  needed to support the added consistency checks.

Out of scope:
- Any new SDK, HTTP, or JSON-RPC product capability.
- Any change to existing observable public behavior beyond protecting and
  validating the behavior already described by the current specs.
- Service runtime regression work, workflow recovery regression work, or M40
  test-infrastructure standardization.
- Repository-wide fixture redesign or generalized test framework abstraction.

## Unchanged Behavior

Behaviors that must not change as a result of this change (leave blank if nothing is at risk):
- The existing `SpecDriven` SDK public behavior remains defined by the current
  SDK specs.
- Existing `/api/v1/*` HTTP API behavior remains unchanged.
- Existing JSON-RPC protocol and dispatcher behavior remains unchanged.
- Protocol-specific transport details that are already intentionally different
  between HTTP and JSON-RPC remain unchanged unless current specs already define
  them as shared observable behavior.
