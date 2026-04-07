# registry-teams

## What

Implement a team registry with Lealone DB persistence, providing team lifecycle management (create, update, dissolve) and member management (join, leave, role assignment). Includes `Team` record, `TeamMember` record, `TeamRole` enum, `TeamStore` interface, and `LealoneTeamStore` implementation following the established Lealone persistence patterns from `LealoneTaskStore`.

## Why

M7 (Task & Team Registries) is in-progress with `registry-tasks` complete. This change is the second and final M7 planned change — completing it closes out M7 and unblocks M8 (Cron Registry). The `Task.owner` field was designed as a free-form string because "teams don't exist yet" (per `registry-tasks` design); this change provides the team infrastructure that `owner` can reference.

## Scope

- `Team` record with fields: id, name, description, status, metadata, createdAt, updatedAt
- `TeamStatus` enum: ACTIVE, DISSOLVED (DISSOLVED is terminal)
- `TeamMember` record with fields: teamId, memberId, role, joinedAt
- `TeamRole` enum: LEAD, MEMBER
- `TeamStore` interface: create, load, update, dissolve, list, joinTeam, leaveTeam, updateRole, listMembers
- `LealoneTeamStore` implementation with two Lealone DB tables (`teams`, `team_members`)
- EventBus integration: publish TEAM_CREATED on create, TEAM_DISSOLVED on dissolve
- Add TEAM_CREATED and TEAM_DISSOLVED to EventType enum
- Unit tests covering team CRUD, member operations, and edge cases

## Unchanged Behavior

- Existing EventType values remain unchanged — only new values added
- EventBus interface and SimpleEventBus implementation unchanged
- Task, TaskStore, LealoneTaskStore unchanged
- AgentContext, SessionStore, AuditLogStore, PolicyStore all unchanged
