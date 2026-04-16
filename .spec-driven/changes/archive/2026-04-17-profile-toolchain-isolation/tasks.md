# Tasks: profile-toolchain-isolation

## Implementation

- [x] Add a config-domain delta spec that extends environment profiles with
  isolated `HOME`, executable-search settings, and explicit Maven/npm/Go/pip
  cache-root behavior.
- [x] Add a platform-domain delta spec that defines how Sandlock-backed
  execution applies the selected profile's PATH, HOME, and cache isolation.
- [x] Extend environment-profile config parsing and selected-profile assembly to
  preserve runtime/home/path fields and profile-specific cache settings.
- [x] Apply resolved profile isolation settings to Sandlock-backed execution and
  fail explicitly when required home/cache settings are missing or invalid.
- [x] Add or update focused unit tests for config validation, selected-profile
  preservation, and Sandlock isolation behavior.
- [x] Confirm the proposal keeps BashTool binding, background-process binding,
  loop-phase binding, and governance/audit additions out of scope for this
  change.

## Testing

- [x] Run validation command `mvnd validate`.
- [x] Run unit test command `mvnd test -Dtest=ConfigLoaderTest,SdkBuilderTest,LealonePlatformTest`.

## Verification

- [x] Verify the proposal reflects the accepted decisions: four-family support,
  minimal observable field sets, and isolated `HOME` plus explicit
  Maven/npm/Go/pip cache roots.
- [x] Run
  `node /home/code/.agents/skills/roadmap-recommend/scripts/spec-driven.js verify profile-toolchain-isolation`.
