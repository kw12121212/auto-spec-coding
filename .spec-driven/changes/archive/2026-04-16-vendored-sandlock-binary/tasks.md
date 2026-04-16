# Tasks: vendored-sandlock-binary

## Implementation

- [x] Add delta specs for repository-bundled Sandlock binary resolution,
  pinned-version behavior, and explicit diagnostics
- [x] Vendor the pinned upstream Sandlock `v0.6.0` Linux x86_64 binary artifact
  under `depends/` and ensure the repository tracks it
- [x] Update `LealonePlatform` Sandlock runtime resolution to prefer explicit
  override first, then the repository-bundled executable, without requiring a
  host `PATH` installation
- [x] Add or update tests for bundled-path resolution, override precedence, and
  missing bundled-artifact failure behavior

## Testing

- [x] Run validation command `mvn validate`
- [x] Run unit test command `mvn test -Dtest=LealonePlatformTest,SdkBuilderTest`

## Verification

- [x] Verify the runtime now defaults to the repository-bundled pinned Sandlock
  artifact instead of depending on an ambient host `PATH` installation
- [x] Run `node /home/code/.agents/skills/spec-driven-auto/scripts/spec-driven.js verify vendored-sandlock-binary`
