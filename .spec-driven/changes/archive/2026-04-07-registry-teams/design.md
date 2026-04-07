# Design: registry-teams

## Approach

Follow the established Lealone DB persistence pattern used by `LealoneTaskStore`, `LealoneSessionStore`, `LealoneAuditLogStore`, and `LealonePolicyStore`:

1. Define `Team` record, `TeamStatus` enum, `TeamMember` record, and `TeamRole` enum in `org.specdriven.agent.registry`
2. Define `TeamStore` interface with team CRUD + member management operations
3. Implement `LealoneTeamStore` with two tables (`teams`, `team_members`), EventBus integration, and background cleanup for DISSOLVED teams
4. Add `TEAM_CREATED` and `TEAM_DISSOLVED` to `EventType` enum
5. Use `MERGE INTO` for upsert, `PreparedStatement` for queries, `JsonObject` for metadata serialization — matching existing patterns

### Table Schemas

```sql
CREATE TABLE IF NOT EXISTS teams (
    id          VARCHAR(36)  PRIMARY KEY,
    name        VARCHAR(255) NOT NULL,
    description CLOB,
    status      VARCHAR(20)  NOT NULL,
    metadata    CLOB,
    created_at  BIGINT       NOT NULL,
    updated_at  BIGINT       NOT NULL
)

CREATE TABLE IF NOT EXISTS team_members (
    team_id     VARCHAR(36)  NOT NULL,
    member_id   VARCHAR(255) NOT NULL,
    role        VARCHAR(20)  NOT NULL,
    joined_at   BIGINT       NOT NULL,
    PRIMARY KEY (team_id, member_id)
)
```

### Member Management

- `joinTeam` inserts a row into `team_members`. Throws `IllegalStateException` if the member is already in the team.
- `leaveTeam` deletes the row from `team_members`. Throws `NoSuchElementException` if the member is not in the team.
- `updateRole` updates the `role` column. Throws `NoSuchElementException` if the member is not in the team.
- When a team is dissolved, all rows in `team_members` for that team are deleted.

## Key Decisions

- **Two normalized tables** — Team metadata is separate from member data. This avoids JSON arrays for members and enables efficient member queries (join/leave/check membership).
- **Composite PK (team_id, member_id)** — A member can only be in a team once. The composite key enforces this at the DB level.
- **`TeamRole` enum: LEAD, MEMBER** — Simple two-role model. A team can have multiple LEADs. Matches the Claude Code team model where teammates are assigned by name with roles.
- **DISSOLVED is soft-delete** — Same pattern as TaskStatus.DELETED. `dissolveTeam()` sets status to DISSOLVED and removes all members. Background cleanup removes DISSOLVED teams older than 7 days.
- **TeamMember as a separate record** — Not embedded in Team. Members are managed independently through dedicated store methods. Keeps Team lightweight for list/query operations.
- **EventBus integration** — Same pattern as `LealoneTaskStore`: publish events on team lifecycle transitions.
- **New EventType values** — Add `TEAM_CREATED` and `TEAM_DISSOLVED` to the existing enum. The spec says "MAY be extended in future milestones."
- **Same package `org.specdriven.agent.registry`** — Consistent with the `registry-tasks` design note that M7 registry code lives here.

## Alternatives Considered

- **Single teams table with members as JSON array** — Rejected; normalized tables enable efficient membership checks and avoid full-table scans for member operations.
- **TeamMember with role hierarchy** — Rejected as over-engineering. A simple LEAD/MEMBER split covers the Claude Code model. Can add roles later if needed.
- **In-memory TeamStore** — Rejected for same reason as tasks: Lealone persistence is a core project goal and the pattern is established.
