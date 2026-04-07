# Tasks: registry-teams

## Implementation

- [x] Add `TEAM_CREATED` and `TEAM_DISSOLVED` to `EventType` enum
- [x] Create `TeamStatus` enum in `org.specdriven.agent.registry` with states: ACTIVE, DISSOLVED; DISSOLVED is terminal
- [x] Create `TeamRole` enum in `org.specdriven.agent.registry` with values: LEAD, MEMBER
- [x] Create `Team` record in `org.specdriven.agent.registry` with fields: id, name, description, status, metadata, createdAt, updatedAt; immutable with defensive metadata copy
- [x] Create `TeamMember` record in `org.specdriven.agent.registry` with fields: teamId, memberId, role, joinedAt
- [x] Create `TeamStore` interface in `org.specdriven.agent.registry` with: create, load, update, dissolve, list, joinTeam, leaveTeam, updateRole, listMembers
- [x] Implement `LealoneTeamStore` with auto-creating `teams` and `team_members` tables, MERGE INTO for upsert, PreparedStatement for queries
- [x] Implement member operations in `LealoneTeamStore`: joinTeam, leaveTeam, updateRole, listMembers with proper error handling
- [x] Implement EventBus integration: publish TEAM_CREATED on create, TEAM_DISSOLVED on dissolve; log publish failures as warnings
- [x] Implement background cleanup: VirtualThread deletes DISSOLVED teams (and members) older than 7 days every hour
- [x] Update `event-system.md` spec to document TEAM_CREATED and TEAM_DISSOLVED as required EventType values

## Testing

- [x] Validation: run `mvn compile` to verify all new code compiles without errors
- [x] Unit test: `TeamTest` — record construction, immutability, metadata defensive copy, null handling
- [x] Unit test: `TeamStatusTest` — validate ACTIVE→DISSOLVED is valid, DISSOLVED is terminal, same-state transition rejected
- [x] Unit test: `LealoneTeamStoreTest` — full team CRUD lifecycle: create → load → update → dissolve → verify DISSOLVED
- [x] Unit test: member operations — joinTeam, leaveTeam, updateRole, listMembers, duplicate join throws, leave non-member throws
- [x] Unit test: edge cases — load non-existent returns empty, dissolve non-existent throws, update non-existent throws, list with no teams returns empty
- [x] Unit test: EventBus integration — verify TEAM_CREATED event on create, TEAM_DISSOLVED event on dissolve
- [x] Run `mvn test -pl . -Dtest="org.specdriven.agent.registry.*Test"` to execute all registry tests

## Verification

- [x] Verify all TaskStore tests still pass (no regression)
- [x] Verify EventType serialization handles new values (Event.toJson / Event.fromJson round-trip)
- [x] Verify LealoneTeamStore matches design table schemas
- [x] Verify proposal scope is fully covered by tests
