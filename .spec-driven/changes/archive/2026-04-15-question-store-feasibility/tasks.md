# Tasks: question-store-feasibility

## Implementation

- [x] Review `LealoneQuestionStore`, `QuestionStore`, `Question`, and existing `LealoneQuestionStoreTest` behavior before editing code.
- [x] Document the positive feasibility boundary in implementation notes or code-adjacent tests by keeping the migration limited to `LealoneQuestionStore`.
- [x] Add the minimum question storage mapping for the existing `questions` table columns.
- [x] Update `LealoneQuestionStore.save`, `update`, `findBySession`, `findByStatus`, `findPending`, and `delete` to preserve the public contract while using the mapped storage path where practical.
- [x] Preserve Store-owned table initialization and the existing constructor contract.

## Testing

- [x] Add or update focused JUnit tests covering Store round-trip behavior, raw SQL table visibility for Store-written rows, Store readability for compatible raw SQL rows, status updates, session/status filtering, pending lookup, and deletion.
- [x] Run validation build command `mvn -q -DskipBuiltinToolsDownload=true -DskipTests compile`.
- [x] Run focused unit tests command `mvn -q -DskipBuiltinToolsDownload=true -Dtest=LealoneQuestionStoreTest test`.

## Verification

- [x] Run `node /home/wx766/.agents/skills/spec-driven-verify/scripts/spec-driven.js verify question-store-feasibility`.
- [x] Verify the implementation matches the proposal scope and does not migrate other Stores.
- [x] Verify no generic ORM repository or broad data-access abstraction was introduced.
