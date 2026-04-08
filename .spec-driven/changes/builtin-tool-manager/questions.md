# Questions: builtin-tool-manager

## Open

- [ ] Q: Which platforms should be supported in the initial implementation?
  Context: GitHub Releases provide binaries for many OS/arch combinations. Minimum viable set is probably Linux x86_64, macOS ARM64, and macOS x86_64. Windows support adds complexity (different archive format, PATH separator).

- [ ] Q: Should the GitHub API requests use authentication (GITHUB_TOKEN) to avoid rate limits?
  Context: Unauthenticated GitHub API has a 60 requests/hour rate limit. Authenticated requests have 5000/hour. For a tool manager that runs once per session, unauthenticated is likely sufficient, but CI environments may hit the limit.

- [ ] Q: Should downloaded binary versions be upgradeable (re-download when a newer version is available)?
  Context: Current design pins version in the enum constant. Users would need to update the SDK to get newer tool versions. An auto-upgrade mechanism would check for newer releases but adds complexity.

## Resolved

<!-- Resolved questions are moved here with their answers -->
