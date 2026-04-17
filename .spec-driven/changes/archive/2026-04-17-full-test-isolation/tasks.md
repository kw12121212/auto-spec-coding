# Tasks: full-test-isolation

## Implementation

- [x] Add a release verification spec delta for stable repo-local Maven tests
- [x] Apply the minimal Maven/JUnit isolation fix needed for reliable full-suite execution
- [x] Add any narrowly scoped test isolation adjustments only if configuration changes alone are insufficient

## Testing

- [x] Run validation command `mvn -q -DskipBuiltinToolsDownload=true -Dtest=SpecDrivenTest,SdkBuilderEventTest,LealoneAuditLogStoreTest test`
- [x] Run unit test command `mvn -q -DskipBuiltinToolsDownload=true test`

## Verification

- [x] Verify the change artifacts with `node /home/code/.agents/skills/spec-driven-propose/scripts/spec-driven.js verify full-test-isolation`
