# Questions: class-cache-manager

## Open

<!-- No open questions -->

## Resolved

- [x] Q: Should `ClassCacheManager` be a standalone interface/impl pair or an internal implementation detail of `LealoneSkillSourceCompiler`?
  Context: Determines whether `class-cache-manager.md` defines a public spec interface or is folded into the compiler spec. Affects testability and replaceability of the cache layer.
  A: Standalone interface — confirmed by user.
