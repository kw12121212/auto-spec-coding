# Design: question-store-feasibility

## Approach

Start by treating feasibility as a bounded gate, not as a broad ORM mandate.
Review `LealoneQuestionStore`, `QuestionStore`, `Question`, the existing
`questions` table shape, and `LealoneQuestionStoreTest` before implementation.
The current evidence supports proceeding: the Store has one table, a small CRUD
surface, no joins, no raw SQL escape hatch requirement, and behavior that can be
verified through the public Store API plus direct table checks.

Add the smallest question storage model mapping needed to represent the existing
`questions` table columns. Then adapt `LealoneQuestionStore` so public methods
continue to accept and return `Question` values while using the mapped storage
path where practical. Table creation should remain Store-owned so fresh
embedded databases continue to work without an external migration step.

Tests should exercise behavior through public methods and the existing table
boundary. The key checks are: save through the Store and read through raw SQL,
insert a compatible table row and read through the Store, update status, find by
session, find by status, find pending, delete, and preserve all persisted
question fields. These tests provide feasibility evidence without coupling to
private ORM internals.

## Key Decisions

- Treat the current feasibility outcome as positive but narrow. Repository
  evidence shows the migration can be attempted without expanding M31 scope.
- Keep `LealoneQuestionStore` as the only Store changed here. The roadmap calls
  for a candidate-specific feasibility step before coexistence policy and
  adoption guidelines.
- Preserve the existing `questions` schema. This change validates ORM adoption,
  not schema migration.
- Keep `QuestionStore` unchanged. M31 requires migrated Stores to preserve
  existing interface signatures.
- Keep table creation inside `LealoneQuestionStore` so test and embedded
  database setup remain unchanged.
- Use Store/table interoperability tests instead of tests that assert ORM
  internals.
- Do not introduce a generic repository or wide data-access abstraction. The
  remaining M31 changes should decide broader coexistence and adoption rules
  after this second Store provides evidence.

## Alternatives Considered

- Leave this as an analysis-only artifact: rejected because the current Store is
  simple enough to produce stronger evidence through a bounded migration and
  behavioral tests.
- Migrate all question-related persistence paths together: rejected because the
  roadmap item names only `LealoneQuestionStore` feasibility, and delivery,
  answer collection, mobile callbacks, and interactive session behavior are
  separate concerns.
- Introduce a generic ORM repository: rejected as speculative and contrary to
  the milestone's warning against early abstraction.
- Change the `questions` table shape to better fit ORM conventions: rejected
  because existing row compatibility is part of the safety boundary.
- Skip directly to `orm-jdbc-coexistence`: rejected because coexistence rules
  should use evidence from both the delivery-log pilot and this question-store
  candidate.
- Start M32 platform core now: rejected because M32 depends on M31 and should
  wait for the ORM direction to be better grounded.
