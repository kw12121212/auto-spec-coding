# orm-jdbc-coexistence

## What

Prove that the M31 ORM pilot Stores can safely coexist with raw JDBC-backed
Stores on the same Lealone embedded database.

This change should add a focused coexistence path around
`LealoneDeliveryLogStore` and `LealoneQuestionStore` as the ORM-backed Stores,
plus one representative raw JDBC Store such as `LealonePolicyStore`. The
observable result is that all Stores can initialize against the same JDBC URL,
create or use their own tables, interleave writes, and read back their own data
without table conflicts, data loss, or public contract changes.

## Why

M31 has already completed the delivery-log ORM pilot and the question-store
feasibility migration. Before writing final adoption guidelines or starting M32
platform unification, the roadmap needs evidence that ORM usage does not force a
big-bang migration of every Lealone-backed Store.

Coexistence is the safety gate between isolated pilots and broader adoption. It
lets migrated Stores use Lealone ORM while existing raw JDBC Stores continue to
work in the same database instance. That evidence is necessary for
`orm-adoption-guidelines` and lowers risk for M32's future Lealone platform
configuration and lifecycle work.

## Scope

In scope:

- Verify `LealoneDeliveryLogStore`, `LealoneQuestionStore`, and a representative
  raw JDBC Store can be constructed with the same embedded Lealone JDBC URL.
- Verify Store-owned table initialization remains non-destructive when ORM and
  raw JDBC Stores initialize in any supported order used by the test.
- Verify interleaved writes through ORM-backed Stores and the raw JDBC Store
  remain independently readable through their public Store APIs.
- Verify existing table interoperability remains intact for the ORM-backed
  question and delivery-log tables while a raw JDBC Store also uses the database.
- Add focused JUnit coverage that documents the coexistence boundary and can be
  reused as evidence for the final M31 adoption guidelines.

Out of scope:

- Migrating additional Stores to ORM.
- Changing `QuestionStore`, `DeliveryLogStore`, or `PolicyStore` method
  signatures.
- Introducing a generic repository, transaction manager, or wide data-access
  abstraction.
- Changing table schemas or adding database migration tooling.
- Starting M32 `LealonePlatform` APIs, configuration centers, health checks, or
  platform migration adapters.
- Defining the final ORM adoption policy; that remains the
  `orm-adoption-guidelines` roadmap item.

## Unchanged Behavior

Behaviors that must not change as a result of this change (leave blank if nothing is at risk):

- Existing `LealoneDeliveryLogStore` behavior, ordering, nullable-field handling,
  and table interoperability remain unchanged.
- Existing `LealoneQuestionStore` save, update, lookup, pending, delete, and
  table interoperability behavior remains unchanged.
- Existing raw JDBC Stores such as `LealonePolicyStore` remain raw JDBC-backed
  and keep their public contracts unchanged.
- Store constructors continue to accept the same inputs and own their current
  table initialization behavior.
- Existing tests for individual ORM-backed and raw JDBC Stores continue to pass.
