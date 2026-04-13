# Tasks: skill-source-compiler

## Implementation

- [x] T1: Verify that the current `com.lealone` dependency exposes the `SourceCompiler` capability required by M30, and stop for scope review if it does not
- [x] T2: Add a delta spec for `skill-source-compiler` that defines the compile contract, result model, diagnostics, and infrastructure-failure boundary
- [x] T3: Confirm implementation and test mappings for the new `org.specdriven.skill.compiler` package and its Lealone-backed adapter
- [x] T4: Implement `SkillCompilationDiagnostic`, `SkillCompilationResult`, `SkillCompilationException`, and `SkillSourceCompiler` under `org.specdriven.skill.compiler`
- [x] T5: Implement `LealoneSkillSourceCompiler` with caller-controlled class output, invalid-source diagnostics, and infrastructure-failure handling
- [x] T6: Add `SkillSourceCompilerTest` covering successful compilation, output directory creation, invalid source diagnostics, and missing compiler capability failure

## Testing

- [x] T7: Validation: run `mvn -q -DskipTests compile`
- [x] T8: Unit test: run `mvn -q -Dtest=SkillSourceCompilerTest test`

## Verification

- [x] T9: Run `node /home/code/.agents/skills/roadmap-recommend/scripts/spec-driven.js verify skill-source-compiler` and confirm the proposal artifacts are valid
- [x] T10: Verify the proposed scope still excludes cache persistence, hot loading, and governance work from later M30 and M34 changes
