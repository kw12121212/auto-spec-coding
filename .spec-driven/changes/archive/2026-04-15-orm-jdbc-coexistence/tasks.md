# Tasks: orm-jdbc-coexistence

## Implementation

- [x] Review `LealoneDeliveryLogStore`, `LealoneQuestionStore`,
  `LealonePolicyStore`, and their existing tests before editing code.
- [x] Add focused coexistence coverage using one shared embedded Lealone JDBC
  URL for the ORM-backed Stores and the representative raw JDBC Store.
- [x] Verify Store-owned table initialization remains non-destructive when the
  participating Stores initialize against the same database.
- [x] Preserve the existing public contracts and constructor signatures for
  `DeliveryLogStore`, `QuestionStore`, and `PolicyStore`.
- [x] Avoid introducing a generic repository, transaction manager, or broad ORM
  abstraction.

## Testing

- [x] Add or update JUnit tests covering shared-database initialization,
  interleaved ORM/raw-JDBC writes, public API readback, and ORM table
  interoperability while the raw JDBC Store also uses the database.
- [x] Run validation build command `mvn -q -DskipBuiltinToolsDownload=true -DskipTests compile`.
- [x] Run focused unit tests command `mvn -q -DskipBuiltinToolsDownload=true -Dtest=OrmJdbcCoexistenceTest test`.

## Verification

- [x] Run `node /home/wx766/.agents/skills/spec-driven-verify/scripts/spec-driven.js verify orm-jdbc-coexistence`.
- [x] Verify the implementation matches the proposal scope and does not migrate
  additional Stores to ORM.
- [x] Verify existing focused Store tests still pass for delivery-log, question,
  and policy storage behavior.
