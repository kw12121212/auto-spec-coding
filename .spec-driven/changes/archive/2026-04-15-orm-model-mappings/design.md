# Design: orm-model-mappings

## Approach

Implementation should begin by reviewing the Lealone ORM `Model` API already
available through the `lealone-orm` dependency and the current
`LealoneDeliveryLogStore` JDBC behavior.

Add the smallest delivery-log model mapping needed to represent the existing
`delivery_log` table columns. Then adapt `LealoneDeliveryLogStore` so its public
methods continue to accept and return `DeliveryAttempt` while internally using
the mapped representation where practical. The Store remains responsible for
auto-creating the current table and for converting between persisted rows and
the immutable `DeliveryAttempt` record.

Tests should verify behavior through the public Store API and table-compatible
rows, not by asserting internal ORM implementation details. The follow-up
`delivery-log-orm-pilot` change can expand the migration once this mapping
contract is proven.

## Key Decisions

- Start with delivery-log mapping only. It is the roadmap's preferred pilot and
  has a narrow, measurable CRUD surface.
- Keep `DeliveryLogStore` unchanged. M31 explicitly requires migrated Stores to
  preserve existing interface signatures.
- Preserve the existing `delivery_log` schema. The mapping is an adoption step,
  not a schema migration.
- Do not add a generic ORM base repository. A project-wide abstraction would be
  speculative before the pilot has shown enough value.
- Treat ORM-generated internal identifiers as storage details. Public behavior
  remains based on `DeliveryAttempt` fields and question lookup methods.

## Alternatives Considered

- Start with `delivery-log-orm-pilot` directly: rejected because the roadmap
  separates mapping definition from the pilot migration, and the mapping is the
  lower-risk first step.
- Migrate `LealoneQuestionStore` first: rejected because the milestone notes
  call for DeliveryLogStore before QuestionStore, and QuestionStore has a larger
  lifecycle surface.
- Introduce a generic Store ORM framework now: rejected as broader than the
  current planned change and contrary to the milestone's warning against early
  abstraction.
- Change the `delivery_log` table shape: rejected because compatibility with
  existing persisted rows is part of the risk boundary for this pilot.
