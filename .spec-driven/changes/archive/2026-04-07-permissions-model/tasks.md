# Tasks: permissions-model

## Implementation

- [x] Update the permission delta spec to define `PermissionDecision`, the revised permission provider evaluation contract, and default policy behavior
- [x] Update the shared tool delta spec to describe how tools consume structured permission decisions
- [x] Update the existing tool delta specs that currently rely on permission checks (`bash`, `file-ops`, `tool-grep`, and `tool-glob`) so their observable behavior distinguishes `deny` from `confirm`
- [x] Implement the permission model changes in code without adding orchestration hooks or DB-backed policy storage

## Testing

- [x] Run validation with `node /home/code/.agents/skills/roadmap-recommend/scripts/spec-driven.js verify permissions-model`
- [x] Run unit tests with `mvn test`
- [x] Add or update JUnit 5 unit tests covering allow, deny, and confirm outcomes for the permission model and affected tools

## Verification

- [x] Verify the implemented behavior matches the proposal scope and does not include `permissions-hooks` or `permission-policy-store` behavior
- [x] Verify the main specs changed by this proposal stay aligned with the permission decision model
