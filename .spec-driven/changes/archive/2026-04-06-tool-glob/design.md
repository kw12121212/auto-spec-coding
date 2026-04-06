# Design: tool-glob

## Approach

Follow the same implementation pattern as `GrepTool`:

1. Walk the directory tree using `Files.walk()`, filtering to regular files only
2. Apply `PathMatcher` with `glob:` syntax against relative paths from the search root
3. Collect matching paths and sort by `Files.getLastModifiedTime()` descending
4. Return file paths joined by newlines
5. Apply `head_limit` to cap the number of results

GlobTool reuses the `resolvePath()` and `stringParam()`/`intParam()` helper patterns already established in `GrepTool`. These should be extracted to a shared utility in a future refactor, but per YAGNI we keep the duplication for now.

## Key Decisions

- **PathMatcher glob syntax**: Use `glob:` prefix with `PathMatcher` — standard JDK, no dependencies
- **Sort order**: Most recently modified first — matches the behavior described in the roadmap
- **Symlink handling**: Skip symbolic links during traversal, consistent with GrepTool
- **Path display**: Return absolute paths in results for clarity

## Alternatives Considered

- **DirectoryStream with glob filter**: Simpler but does not sort by modification time efficiently
- **Third-party glob library**: Unnecessary — `PathMatcher` covers standard glob syntax
