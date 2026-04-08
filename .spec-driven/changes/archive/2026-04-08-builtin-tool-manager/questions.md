# Questions: builtin-tool-manager

## Open

<!-- No open questions -->

## Resolved

- [x] Q: Which platforms should be supported in the initial implementation?
  Answer: Linux x86_64 + macOS (ARM64 + x86_64). Windows support deferred to follow-up.

- [x] Q: Should the GitHub API requests use authentication (GITHUB_TOKEN) to avoid rate limits?
  Answer: No authentication for initial implementation. Add GITHUB_TOKEN support later if rate limiting becomes an issue.

- [x] Q: Should downloaded binary versions be upgradeable (re-download when a newer version is available)?
  Answer: No auto-upgrade. Version pinned in enum constant. Upgrade by updating SDK version.
