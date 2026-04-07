# Agent Interface Spec (Delta: agent-session-store)

## ADDED Requirements

### Requirement: Session record

- MUST be a Java record in `org.specdriven.agent.agent` with fields: `id` (String), `state` (AgentState), `createdAt` (long, epoch millis), `updatedAt` (long, epoch millis), `expiryAt` (long, epoch millis), `conversation` (Conversation)
- `id` MAY be null before first save; after save it MUST be a non-empty UUID string
- `expiryAt` MUST be set to `createdAt + 30 days` on construction and MUST NOT be modified by subsequent saves

### Requirement: SessionStore interface

- MUST be a public interface in `org.specdriven.agent.agent`
- MUST define `save(Session session)` returning String (the session ID); MUST generate a UUID if `session.id()` is null
- MUST define `load(String sessionId)` returning `Optional<Session>`
- MUST define `delete(String sessionId)` returning void
- MUST define `listActive()` returning `List<Session>` — all sessions whose `expiryAt` is in the future

### Requirement: LealoneSessionStore implementation

- MUST implement `SessionStore` in `org.specdriven.agent.agent`
- MUST persist sessions to two Lealone SQL tables: `agent_sessions` (structured columns) and `agent_messages` (one row per Message, content as JSON CLOB)
- MUST auto-create both tables on first initialization if they do not exist
- MUST serialize Message content using `com.lealone.orm.json.JsonObject`
- MUST start a background VirtualThread on initialization that deletes expired sessions and their messages every hour
- Background cleanup failures MUST be logged as warnings and MUST NOT propagate exceptions to callers

### Requirement: SimpleAgentContext SessionStore integration

- `SimpleAgentContext` MUST provide an additional constructor accepting `SessionStore` as an optional parameter
- The existing constructor without `SessionStore` MUST remain valid and unchanged in behavior
- When a `SessionStore` is present, `DefaultAgent.doExecute` MUST call `store.load(sessionId)` before invoking the orchestrator, and `store.save(session)` after the orchestrator completes or on state transition to STOPPED or ERROR

## UNCHANGED Requirements

- All existing Agent lifecycle, state machine, Conversation, Message, Orchestrator, and AgentContext requirements remain in effect without modification
