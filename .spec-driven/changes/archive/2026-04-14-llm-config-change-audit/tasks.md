# Tasks: llm-config-change-audit

## Implementation

- [x] Add `LLM_CONFIG_CHANGE_REJECTED` to `EventType` enum in `src/main/java/org/specdriven/agent/event/EventType.java`
- [x] Add `operator` field to `publishConfigChanged` metadata in `DefaultLlmProviderRegistry` — derive from session ID or `"system"` for default scope
- [x] Add `publishConfigRejected(String scope, String sessionId, String result, String reason)` private method to `DefaultLlmProviderRegistry` that publishes `LLM_CONFIG_CHANGE_REJECTED` events with metadata: `scope`, `sessionId` (if session-scoped), `operator`, `result`, `reason`
- [x] Call `publishConfigRejected` in the permission-denied path of `requirePermission` before throwing `SetLlmSqlException`
- [x] Call `publishConfigRejected` in `applySetLlmStatement` for parsing and validation failure paths before throwing `SetLlmSqlException`
- [x] Call `publishConfigRejected` in `clearSessionSnapshot` permission-denied path before throwing `SetLlmSqlException`
- [x] Update `llm-runtime-config.md` main spec with audit requirements
- [x] Update `event-system.md` main spec with new event type

## Testing

- [x] Lint: run `mvn compile -pl . -q` to validate compilation
- [x] Add test: `SET LLM` with permission DENY publishes `LLM_CONFIG_CHANGE_REJECTED` event before exception
- [x] Add test: `SET LLM` with permission CONFIRM publishes `LLM_CONFIG_CHANGE_REJECTED` event with `result = "confirm_required"`
- [x] Add test: `SET LLM` with unsupported key publishes `LLM_CONFIG_CHANGE_REJECTED` event with `result = "validation_failed"`
- [x] Add test: `SET LLM` with invalid value publishes `LLM_CONFIG_CHANGE_REJECTED` event with `result = "validation_failed"`
- [x] Add test: `SET LLM` with parse error publishes `LLM_CONFIG_CHANGE_REJECTED` event with `result = "parse_error"`
- [x] Add test: `clearSessionSnapshot` with permission DENY publishes `LLM_CONFIG_CHANGE_REJECTED` event
- [x] Add test: successful `SET LLM` does NOT publish `LLM_CONFIG_CHANGE_REJECTED`
- [x] Add test: `LLM_CONFIG_CHANGED` event metadata includes `operator` field for session-scoped change
- [x] Add test: `LLM_CONFIG_CHANGED` event metadata includes `operator = "system"` for default-scope change
- [x] Add test: `LLM_CONFIG_CHANGE_REJECTED` event metadata includes `operator` field
- [x] Unit test: run `mvn test -pl . -q` to verify all tests pass

## Verification

- [x] Run `mvn test -pl .` — all tests green
- [x] Run `node /home/wx766/.claude/skills/roadmap-recommend/scripts/spec-driven.js verify llm-config-change-audit`
- [x] Verify `LLM_CONFIG_CHANGE_REJECTED` event metadata never contains API key or vault reference values
