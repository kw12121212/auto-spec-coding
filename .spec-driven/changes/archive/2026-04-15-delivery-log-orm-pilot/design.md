# Design: delivery-log-orm-pilot

## Approach

Start from the existing `DeliveryLogModel` and `LealoneDeliveryLogStore`
behavior introduced by `orm-model-mappings`. Review the Store's public contract,
table initialization SQL, conversion between `DeliveryAttempt` and persisted
rows, and current tests before making any implementation edits.

The implementation should keep `LealoneDeliveryLogStore` as the only migrated
Store in this change. Its public methods should continue to accept and return
`DeliveryAttempt` values, while the internal save and lookup paths use the
delivery-log mapping where practical. Table creation can remain Store-owned so
fresh embedded databases continue to work without an external migration step.

Tests should exercise the Store through public methods and through the existing
`delivery_log` table boundary: save through the Store and verify the row is
visible through raw SQL, insert a compatible row through raw SQL and verify the
Store reads it, and keep the existing round-trip, ordering, latest lookup, empty
result, and nullable-field checks. This proves the pilot behavior without
asserting private ORM internals.

## Key Decisions

- Keep the pilot limited to `LealoneDeliveryLogStore`. M31 explicitly calls this
  the first target and warns against broad Store migration before the pilot
  proves value.
- Preserve the existing `delivery_log` schema. The pilot validates ORM adoption,
  not schema migration.
- Keep `DeliveryLogStore` unchanged. Migrated Stores must preserve existing
  interface signatures.
- Treat ORM-generated IDs as storage details. Public behavior remains based on
  `DeliveryAttempt` fields and question lookup methods.
- Use behavioral tests at the Store/table boundary instead of tests coupled to
  ORM implementation details.
- Avoid adding an adoption policy here. The pilot can produce evidence, but the
  policy belongs in `orm-adoption-guidelines`.

## Alternatives Considered

- Migrate `LealoneQuestionStore` now: rejected because the roadmap orders the
  delivery-log pilot first, and QuestionStore has a broader lifecycle surface.
- Introduce a generic ORM repository: rejected as speculative and contrary to
  the milestone's warning about premature abstraction.
- Convert every delivery-related persistence path in one change: rejected
  because the roadmap defines this as a narrow pilot and later coexistence and
  adoption changes handle broader policy.
- Change the `delivery_log` table shape to better fit ORM conventions: rejected
  because existing row compatibility is a core risk boundary for this pilot.
- Start M32 platform core now: rejected because M32 lists M31 as a dependency
  and should wait for concrete DB/ORM pilot evidence.
