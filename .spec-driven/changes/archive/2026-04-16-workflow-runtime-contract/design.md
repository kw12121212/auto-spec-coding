# Design: workflow-runtime-contract

## Approach

- Introduce workflow as a new top-level spec area centered on observable declaration and instance lifecycle behavior rather than on implementation structure.
- Define one shared workflow vocabulary across SDK, HTTP REST, JSON-RPC, and audit/event surfaces so later M37 changes extend the same workflow instance model.
- Keep the first contract narrow: declaration parity, lifecycle states, start behavior, state query, result retrieval, and minimum lifecycle audit visibility.

## Key Decisions

- Define declaration parity across SDK/domain and the first supported Lealone SQL workflow declaration path.
  Rationale: the user explicitly chose both forms now, and later workflow features need one shared definition boundary instead of separate declaration models.
- Expose workflow start, state query, and result retrieval across SDK, HTTP REST, and JSON-RPC in the first change.
  Rationale: delaying transport parity would let each surface evolve different workflow semantics and payload shapes.
- Include `WAITING_FOR_INPUT` in the first lifecycle contract while deferring detailed human-bridge behavior.
  Rationale: later human-in-the-loop work needs a stable observable waiting state, but this change should not yet specify routing, delivery, or resume mechanics.
- Keep workflow composition, retry, and checkpoint recovery out of scope.
  Rationale: those are separate planned changes in M37 and depend on a stable runtime contract first.
- Keep workflow runtime explicitly distinct from the spec-driven change pipeline.
  Rationale: roadmap and loop infrastructure may share building blocks with workflow runtime, but they must not share one semantic boundary.

## Alternatives Considered

- Start with an SDK-only workflow contract.
  Rejected because the user chose immediate SDK + HTTP + JSON-RPC coverage, and delaying transport parity would create avoidable divergence in lifecycle semantics.
- Start with SQL declaration only and defer the domain declaration path.
  Rejected because public SDK callers still need an in-process declaration contract, and later workflow composition changes should not depend on SQL-only registration.
- Fold workflow composition, human bridge, and recovery into this first contract change.
  Rejected because that would skip the intended dependency order inside M37 and make the first proposal too broad to stay stable.
