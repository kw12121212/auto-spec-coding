# question-store-feasibility

## What

Evaluate `LealoneQuestionStore` as the next ORM adoption candidate after the
completed delivery-log pilot, and proceed with a narrow migration only if the
current Store surface proves simple enough to keep observable behavior stable.

Repository evidence already shows a positive feasibility path: the Store has a
small CRUD-style surface, a single existing `questions` table, no complex joins,
and focused tests. This change should therefore introduce the minimum question
storage mapping needed to preserve the existing `QuestionStore` contract and use
that mapping for `LealoneQuestionStore` persistence where practical.

## Why

M31 is intentionally a controlled ORM adoption milestone. The delivery-log ORM
pilot is complete, but M31 still needs evidence about whether a second Store can
benefit from ORM without adding broad abstractions or hidden complexity.

`LealoneQuestionStore` is the roadmap's next candidate. It is important because
question state sits on the human-in-loop and mobile reply paths, but its storage
surface is still bounded enough for a focused feasibility migration. Completing
this change gives the later `orm-jdbc-coexistence` and
`orm-adoption-guidelines` changes concrete evidence from more than one Store,
and keeps M32 platform-unification work from starting on incomplete DB/ORM
assumptions.

## Scope

In scope:

- Record the feasibility decision for `LealoneQuestionStore` based on current
  code evidence: proceed only with a narrow mapping-backed migration.
- Define the minimum question storage mapping needed for the existing
  `questions` table.
- Preserve the `QuestionStore` interface, `LealoneQuestionStore` constructor
  behavior, and existing `Question` payload fields.
- Preserve table interoperability: Store-written questions remain visible
  through the existing `questions` columns, and compatible table rows remain
  readable through the Store.
- Preserve current save, update, find-by-session, find-by-status, find-pending,
  and delete behavior.
- Add focused JUnit coverage for round-trip behavior, table interoperability,
  ordering/filtering behavior, pending lookup behavior, and deletion.

Out of scope:

- Migrating Stores other than `LealoneQuestionStore`.
- Introducing a generic ORM repository, base Store, or project-wide data access
  abstraction.
- Changing the `QuestionStore` method signatures or the `Question` record
  fields.
- Changing the `questions` schema or adding database migration tooling.
- Changing question routing, answer collection, mobile delivery, interactive
  session behavior, or event semantics.
- Writing the final ORM adoption policy; that remains the
  `orm-adoption-guidelines` roadmap item.

## Unchanged Behavior

Behaviors that must not change as a result of this change (leave blank if nothing is at risk):

- `QuestionStore.save()` still persists all question fields and returns the
  existing `questionId`.
- `update(questionId, status)` still updates only the persisted status and
  returns the updated question.
- `findBySession(sessionId)` still returns only questions for that session.
- `findByStatus(status)` still returns only questions with that status.
- `findPending(sessionId)` still returns a waiting question for that session and
  ignores questions in other statuses.
- `delete(questionId)` still removes the persisted question.
- Existing rows compatible with the current `questions` table remain readable.
- Existing callers do not need new method signatures, new input fields, or a new
  Store construction contract.
- Raw JDBC-backed Stores outside the question-store feasibility change remain
  unchanged.
