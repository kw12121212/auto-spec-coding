# Questions: tool-fd

## Open

<!-- No open questions -->

## Resolved

- [x] Q: How should BuiltinToolManager be injected into GlobTool?
  Context: GlobTool is currently stateless with a no-arg constructor. Need to determine injection strategy.
  A: Constructor injection with optional parameter. Null means pure Java only. No-arg constructor preserved for backward compatibility.

- [x] Q: How to handle glob pattern syntax differences between Java and fd?
  Context: Java glob and fd glob are not identical. Some patterns may not translate directly.
  A: Use fd `--glob` flag to pass pattern directly. Incompatible patterns cause fd to fail, triggering silent fallback to pure Java.
