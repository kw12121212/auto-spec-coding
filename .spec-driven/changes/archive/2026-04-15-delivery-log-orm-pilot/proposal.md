# delivery-log-orm-pilot

## What

Complete the M31 delivery-log ORM pilot by treating `LealoneDeliveryLogStore` as
the first migrated Store and proving that its persisted behavior remains
compatible with the existing `delivery_log` table and `DeliveryLogStore`
contract.

The change should use the already-defined delivery-log ORM mapping as the
production persistence path for saves and lookups where practical, while keeping
the public Store API, constructor behavior, table shape, and returned
`DeliveryAttempt` values unchanged.

## Why

M31 is intentionally a controlled ORM adoption milestone. The previous
`orm-model-mappings` change established the minimum delivery-log mapping; the
next step is to close the pilot by validating real Store behavior through that
path before considering `LealoneQuestionStore`, ORM/JDBC coexistence rules, or
broader adoption guidelines.

`LealoneDeliveryLogStore` is the safest pilot target because it has a narrow
CRUD surface, an existing Lealone table, focused unit tests, and clear
round-trip semantics. Finishing this pilot reduces risk for the remaining M31
items and keeps M32 platform-unification work from starting before the DB/ORM
direction has concrete evidence.

## Scope

In scope:

- Confirm `LealoneDeliveryLogStore.save`, `findByQuestion`, and
  `findLatestByQuestion` operate through the delivery-log mapped storage path
  while preserving observable behavior.
- Verify Store-written attempts remain visible through the existing
  `delivery_log` table columns to raw SQL readers.
- Verify rows compatible with the existing `delivery_log` table remain readable
  through the Store.
- Preserve the `DeliveryLogStore` interface, `DeliveryAttempt` fields, and
  `LealoneDeliveryLogStore` construction contract.
- Add or update focused JUnit coverage that demonstrates behavioral parity and
  table interoperability.
- Record pilot evidence useful for later M31 decisions without creating a broad
  ORM adoption rule in this change.

Out of scope:

- Migrating `LealoneQuestionStore` or any other Store.
- Introducing a generic ORM repository, base Store, or project-wide data access
  abstraction.
- Changing the `delivery_log` schema or adding database migration tooling.
- Changing delivery retry behavior, mobile channel behavior, HTTP endpoints, or
  event emission.
- Writing the final ORM adoption guidelines; that remains the
  `orm-adoption-guidelines` roadmap item.

## Unchanged Behavior

Behaviors that must not change as a result of this change (leave blank if nothing is at risk):

- `DeliveryLogStore.save()` still persists exactly one `DeliveryAttempt`.
- `findByQuestion(questionId)` still returns attempts for that question ordered
  by ascending `attemptNumber`.
- `findLatestByQuestion(questionId)` still returns the attempt with the highest
  `attemptNumber` or empty when none exists.
- Unknown questions still return empty results.
- Nullable `statusCode` and `errorMessage` fields still round-trip as null.
- Existing `delivery_log` rows that match the current table columns remain
  readable.
- Existing callers do not need new method signatures, new input fields, or a new
  Store construction contract.
- Raw JDBC-backed Stores outside the delivery-log pilot remain unchanged.
