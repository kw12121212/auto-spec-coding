# Tasks: environment-profile-contract

## Implementation

- [x] Add a config-domain delta spec for project-level environment profile declaration, selection precedence, and validation behavior
- [x] Modify the config loader delta spec to cover project YAML profile loading and observable diagnostics
- [x] Confirm the proposal keeps Sandlock execution, toolchain isolation, and tool execution binding out of scope for this change

## Testing

- [x] Run validation command `mvn validate`
- [x] Run unit test command `mvn test -Dtest=ConfigLoaderTest,SdkBuilderTest`

## Verification

- [x] Verify the proposal artifacts reflect the accepted decisions: config-domain spec path, YAML-only profile source, and mandatory default-profile resolution
- [x] Run `node /home/code/.agents/skills/roadmap-recommend/scripts/spec-driven.js verify environment-profile-contract`
