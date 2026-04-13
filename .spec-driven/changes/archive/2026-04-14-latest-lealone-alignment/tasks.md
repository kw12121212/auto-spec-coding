# Tasks: latest-lealone-alignment

## Implementation

- [x] T1: Resolve the latest Lealone upstream source baseline used for this refresh and record it in repo-local materials that are visible from the repository
- [x] T2: Add a delta spec for release preparation that defines the verified Lealone baseline, the compatibility verification workflow, and the alignment notes expected from this refresh
- [x] T3: Update build or dependency configuration so the repository actually aligns with the refreshed Lealone upstream source baseline
- [x] T4: Check and adapt the compiler integration path, including `LealoneSkillSourceCompiler`, against the refreshed baseline
- [x] T5: Check and adapt Lealone service-executor SPI integration, including `SkillServiceExecutorFactory`, against the refreshed baseline
- [x] T6: Check and adapt embedded JDBC-backed Lealone store integrations and any directly affected JSON helper usage against the refreshed baseline
- [x] T7: Add or update repo-local documentation or notes that summarize checked integration areas, required compatibility fixes, and clearly separated future-useful upstream capabilities

## Testing

- [x] T8: Validation: run `mvn -q -DskipTests compile`
- [x] T9: Focused unit tests: run `mvn -q -Dtest=SkillSourceCompilerTest,SkillServiceExecutorFactoryTest,LealoneSessionStoreTest,LealoneRuntimeLlmConfigStoreTest,LealoneToolCacheTest,LealoneQuestionStoreTest,LealoneVaultTest,LealoneTaskStoreTest,LealoneTeamStoreTest,LealoneCronStoreTest test`

## Verification

- [x] T10: Run `node /home/code/.agents/skills/spec-driven-brainstorm/scripts/spec-driven.js verify latest-lealone-alignment` and fix any proposal artifact issues that are safe to correct immediately
- [x] T11: Verify the implemented refresh still preserves existing Native Java SDK, JSON-RPC, HTTP API, and Lealone-backed store semantics apart from required compatibility fixes
