# Tasks: sandlock-runner-integration

## Implementation

- [x] Add a platform-domain delta spec for Sandlock-backed command execution,
  structured execution results, and explicit pre-launch diagnostics
- [x] Modify the `LealonePlatform` delta spec to expose an explicit typed
  Sandlock capability through the existing platform assembly path
- [x] Keep BashTool binding, background-process binding, loop command binding,
  and profile toolchain isolation out of scope for this change

## Testing

- [x] Run validation command `mvn validate`
- [x] Run unit test command `mvn test -Dtest=LealonePlatformTest,SdkBuilderTest`

## Verification

- [x] Verify the proposal reuses the existing environment-profile namespace
  instead of introducing a second Sandlock-only profile naming model
- [x] Run `node /home/code/.agents/skills/spec-driven-auto/scripts/spec-driven.js verify sandlock-runner-integration`
