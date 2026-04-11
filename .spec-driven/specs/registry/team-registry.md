# Team Registry Spec

## ADDED Requirements

### Requirement: Team record

- MUST be a Java record in `org.specdriven.agent.registry` with fields: `id` (String), `name` (String), `description` (String), `status` (TeamStatus), `metadata` (Map<String, Object>), `createdAt` (long, epoch millis), `updatedAt` (long, epoch millis)
- MUST be immutable
- Compact constructor MUST defensively copy the metadata map and normalize null to empty map
- `id` MAY be null before first save; after save it MUST be a non-empty UUID string

### Requirement: TeamStatus enum

- MUST define states: ACTIVE, DISSOLVED
- Each state MUST be independently testable

### Requirement: Team state transitions

- MUST enforce the following valid transition: ACTIVE→DISSOLVED
- MUST reject any transition not listed above by throwing IllegalStateException with a descriptive message
- DISSOLVED MUST be a terminal state — no transition away from it is allowed

### Requirement: TeamMember record

- MUST be a Java record in `org.specdriven.agent.registry` with fields: `teamId` (String), `memberId` (String), `role` (TeamRole), `joinedAt` (long, epoch millis)
- MUST be immutable

### Requirement: TeamRole enum

- MUST define values: LEAD, MEMBER
- Each value MUST be independently testable

### Requirement: TeamStore interface

- MUST be a public interface in `org.specdriven.agent.registry`
- MUST define `create(Team)` returning `String` (the team ID); MUST generate a UUID if `team.id()` is null
- MUST define `load(String teamId)` returning `Optional<Team>`
- MUST define `update(String teamId, String name, String description)` returning `Team` — the updated team with new name/description and updatedAt
- MUST define `dissolve(String teamId)` returning void — transitions status to DISSOLVED and removes all members
- MUST define `list()` returning `List<Team>` — all non-dissolved teams ordered by createdAt ascending
- MUST define `joinTeam(String teamId, String memberId, TeamRole role)` returning void — adds a member to the team
- MUST define `leaveTeam(String teamId, String memberId)` returning void — removes a member from the team
- MUST define `updateRole(String teamId, String memberId, TeamRole newRole)` returning void — changes a member's role
- MUST define `listMembers(String teamId)` returning `List<TeamMember>` — all members of the team

### Requirement: LealoneTeamStore implementation

- MUST implement TeamStore in `org.specdriven.agent.registry`
- MUST persist teams to a `teams` table and members to a `team_members` table in Lealone DB
- MUST auto-create both tables on first initialization if they do not exist
- MUST accept EventBus and JDBC URL as constructor parameters
- MUST serialize metadata using `com.lealone.orm.json.JsonObject`
- MUST use `MERGE INTO` for save operations (upsert pattern)
- MUST update `updatedAt` to current time on every save/update
- MUST preserve `createdAt` on updates — only set on initial creation
- MUST be a public class in `org.specdriven.agent.registry`

### Requirement: TeamStore member operations

- `joinTeam` with a member already in the team MUST throw IllegalStateException
- `leaveTeam` on a member not in the team MUST throw NoSuchElementException
- `updateRole` on a member not in the team MUST throw NoSuchElementException
- `joinTeam` on a dissolved team MUST throw IllegalStateException
- `leaveTeam` on a dissolved team MUST throw IllegalStateException

### Requirement: TeamStore EventBus integration

- MUST publish an Event with type TEAM_CREATED when a new team is saved (id was null → generated)
- MUST publish an Event with type TEAM_DISSOLVED when a team transitions to DISSOLVED status
- Event source MUST be "TeamStore"
- Event metadata MUST include `teamId` key with the team's id value
- EventBus publish failures MUST be logged as warnings and MUST NOT propagate exceptions to callers

### Requirement: TeamStore background cleanup

- MUST start a background VirtualThread on initialization that deletes DISSOLVED teams (and their members) older than 7 days every hour
- Background cleanup MUST delete associated `team_members` rows before deleting the team row
- Background cleanup failures MUST be logged as warnings and MUST NOT propagate exceptions to callers

### Requirement: TeamStore query behavior

- `load` with non-existent ID MUST return `Optional.empty()`
- `list` with no teams MUST return an empty list (not null)
- `listMembers` on a dissolved team MUST return an empty list
- `dissolve` on a non-existent team MUST throw NoSuchElementException
- `update` on a non-existent team MUST throw NoSuchElementException

### Requirement: EventType additions

- EventType enum MUST include `TEAM_CREATED` and `TEAM_DISSOLVED` values
- These values MUST follow the existing EventType naming convention
