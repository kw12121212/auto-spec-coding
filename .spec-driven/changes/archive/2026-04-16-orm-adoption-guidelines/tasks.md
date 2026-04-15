# Tasks: orm-adoption-guidelines

## Implementation

- [x] Review archived M31 changes (orm-model-mappings, delivery-log-orm-pilot,
  question-store-feasibility, orm-jdbc-coexistence) to confirm the admission
  criteria and coexistence evidence in the delta spec are accurate
- [x] Add `OrmAdoptionGuidelinesTest.java` in
  `src/test/java/org/specdriven/agent/question/` that initializes
  `LealoneDeliveryLogStore`, `LealoneQuestionStore`, and `LealonePolicyStore`
  with a single shared embedded Lealone JDBC URL, writes one record through
  each Store's public API, and asserts each Store reads its own record back
  correctly
- [x] Merge the new `orm/orm-adoption.md` delta spec into the main
  `.spec-driven/specs/` tree (create `.spec-driven/specs/orm/orm-adoption.md`)
  and add an entry to `.spec-driven/specs/INDEX.md`

## Testing

- [x] Run lint validation: `mvn checkstyle:check` — new test file has 0 violations (pre-existing violations in unrelated files are out of scope)
- [x] Run unit tests: `mvn test -Dtest=OrmAdoptionGuidelinesTest` — confirm
  escape-hatch and coexistence invariants pass

## Verification

- [x] All tasks above are marked complete
- [x] `OrmAdoptionGuidelinesTest` passes with all three Stores interoperating
- [x] The delta spec at `specs/orm/orm-adoption.md` has been merged into the
  main spec index and is reachable from `specs/INDEX.md`
- [x] No existing tests regress (run `mvn test`)
