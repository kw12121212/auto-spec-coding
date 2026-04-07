# Questions: registry-cron

## Open

<!-- No open questions -->

## Resolved

- [x] Q: Does Lealone have a built-in cron/scheduler module?
  Context: Determines whether we use Lealone scheduling or implement our own.
  A: No. Lealone's `Scheduler` is a database event loop, not a cron scheduler. We implement our own cron expression parser and use JDK VirtualThread polling.

- [x] Q: Should one-shot tasks share the CronStore interface or use a separate type?
  Context: Affects API surface and implementation complexity.
  A: Shared interface. One-shot tasks use `delayMillis` field with null `cronExpression`. Same lifecycle, same persistence.
