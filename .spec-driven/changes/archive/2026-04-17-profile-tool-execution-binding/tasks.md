# Tasks: profile-tool-execution-binding

## Implementation

- [x] Update BashTool contract and implementation to accept an optional `profile` parameter and route execution through the resolved environment profile when available.
- [x] Update background-process registration and process metadata contracts so profile-bound launches can preserve and report the resolved profile.
- [x] Update command-backed loop phase execution so workflow commands use the resolved project/default environment profile without adding a loop-specific override.
- [x] Add or update spec delta files for `tools/bash-tool.md`, `tools/background-tool-interface.md`, and `sdk/autonomous-loop.md`.

## Testing

- [x] Run `mvn validate`
- [x] Run unit tests with `mvn test`
- [x] Add or update focused unit tests for BashTool profile selection.
- [x] Add or update focused unit tests for background-process resolved-profile metadata.
- [x] Add or update focused unit tests for command-backed loop phase profile binding.

## Verification

- [x] Run `node /home/code/.agents/skills/spec-driven-auto/scripts/spec-driven.js verify profile-tool-execution-binding`
- [x] Verify the final implementation matches the selected M38 planned-change scope as implemented in the current repository and does not add loop-specific profile override behavior.
