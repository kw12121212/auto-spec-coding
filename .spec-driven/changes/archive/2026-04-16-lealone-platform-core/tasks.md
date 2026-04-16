# Tasks: lealone-platform-core

## Implementation

- [x] Define the public `LealonePlatform` contract and its supported creation path
- [x] Define explicit typed accessors for the four initial platform capability domains: DB, runtime LLM, compiler/hot-load, and interactive session
- [x] Integrate platform assembly with the existing SDK public surface without breaking `SpecDriven.builder()` usage
- [x] Add or update unit tests covering platform construction, typed capability access, and backward compatibility of existing SDK entry paths

## Testing

- [x] Run validation command `mvn compile -pl . -q`
- [x] Run focused unit test command `mvn test -pl . -Dtest="LealonePlatformTest,SdkBuilderTest,SpecDrivenTest" -q -DfailIfNoTests=false`
- [x] Run full unit test command `mvn test -pl . -q`

## Verification

- [x] Verify `LealonePlatform` is publicly accessible and coexists with `SpecDriven`
- [x] Verify the initial platform API uses typed capability access rather than a generic registry lookup
- [x] Verify existing runtime LLM, hot-load, and interactive-session behaviors remain unchanged when accessed through the platform surface
