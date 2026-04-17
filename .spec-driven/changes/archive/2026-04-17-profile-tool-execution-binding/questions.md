# Questions: profile-tool-execution-binding

## Open

<!-- No open questions -->

## Resolved

- [x] Q: Should `BashTool` expose an explicit `profile` input parameter?
  Context: This determines whether Bash callers can intentionally select a non-default environment profile or must always inherit the resolved project profile.
  A: Yes. Add an optional explicit `profile` parameter and use the selected/default profile when it is omitted.
- [x] Q: Should background-process results expose the resolved profile name?
  Context: This determines whether profile-bound background launches are diagnosable from returned observable metadata.
  A: Yes. Returned process metadata should expose the resolved profile name.
- [x] Q: Should loop phase command execution support an explicit override profile in this change?
  Context: This determines whether the proposal expands loop configuration scope beyond default project-profile binding.
  A: No. Keep this proposal minimal and bind loop phase commands only to the resolved project/default profile.
