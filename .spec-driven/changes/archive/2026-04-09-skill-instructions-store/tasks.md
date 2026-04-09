# Tasks: skill-instructions-store

## Implementation

- [x] Add `SkillInstructionStoreException` in `org.specdriven.skill.store`
- [x] Add `SkillInstructionStore` interface with `loadInstructions` and `loadResource` methods
- [x] Add `FileSystemInstructionStore` implementing `SkillInstructionStore` with path-traversal guard in `loadResource`
- [x] Modify `SkillSqlConverter.convert` signature to accept `Path skillDir` instead of `String instructionBody`; emit `'skill_dir'` parameter, remove inline `'instructions'`
- [x] Update `SkillAutoDiscovery` to call the new `SkillSqlConverter.convert(frontmatter, skillDir)` signature

## Testing

- [x] Run `mvn compile` to build and verify no compilation errors
- [x] Run `mvn test -pl . -Dtest="SkillInstructionStoreTest,SkillSqlConverterTest,SkillAutoDiscoveryTest"` for targeted unit tests
- [x] Add `SkillInstructionStoreTest`: loadInstructions returns correct body, loadResource returns file content, loadResource throws on path traversal, loadResource throws on missing file
- [x] Update `SkillSqlConverterTest`: verify `skill_dir` parameter is emitted, verify `instructions` is absent, verify other PARAMETERS unchanged
- [x] Update `SkillAutoDiscoveryTest`: verify generated SQL contains `skill_dir` for each registered skill

## Verification

- [x] Verify implementation matches proposal
