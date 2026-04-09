# Questions: skill-instructions-store

## Open

## Resolved

- [x] Q: Should `skill_dir` be stored as an absolute path or relative to the project root?
  Context: Absolute paths are unambiguous but break if the project is moved or run in CI from a different base directory. A relative path requires a known root (e.g., the `skills/` parent directory).
  A: Absolute path. Skills are registered once at startup from a known filesystem location; the executor reads `skill_dir` from PARAMETERS at invocation time without needing to reconstruct a root. If portability becomes a concern it can be addressed in a later change.
