# Questions: permissions-model

## Open

<!-- No open questions -->

## Resolved

- [x] Q: Should this change introduce a structured permission decision now (`allow` / `deny` / `confirm`), or keep the current boolean contract and defer explicit confirmation behavior to `permissions-hooks`?
  Context: This determines whether the core permission interface changes in this foundational milestone item or remains partially underspecified until a later change.
  A: Introduce the structured `allow` / `deny` / `confirm` decision model now.
