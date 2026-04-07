# Questions: registry-tasks

## Open

<!-- No open questions -->

## Resolved

- [x] Q: Should `TaskStore` be accessible from `AgentContext`, or is it a standalone service used independently?
  Context: AgentContext currently provides sessionId, config, toolRegistry, and conversation. Adding TaskStore would couple agent lifecycle to task management but enable agents to self-manage tasks.
  A: Standalone — no AgentContext integration. Agents can access TaskStore through constructor injection or service locator when needed.

- [x] Q: Should `delete()` be reversible (soft-delete with restore) or truly one-way?
  Context: Current design uses soft-delete (status → DELETED) with background cleanup after 7 days.
  A: One-way soft-delete — DELETED is terminal. No restore() method. 7-day window before permanent row cleanup provides safety margin.
