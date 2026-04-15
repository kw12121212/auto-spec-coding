# orm-adoption-guidelines

## What

Formalize the ORM adoption criteria, applicable scenarios, and escape-hatch
rules that the M31 pilots (delivery-log, question-store, and coexistence)
produced as evidence. The primary deliverable is a new spec file that records
observable requirements governing when and how ORM migration is appropriate,
together with a focused test that validates the documented escape-hatch invariant.

## Why

M31's Done Criteria require the pilot phase to produce admission criteria that
prevent the ORM coverage from expanding without clear benefit. Four M31 changes
are now archived:

- `orm-model-mappings` — delivery-log ORM mapping defined
- `delivery-log-orm-pilot` — `LealoneDeliveryLogStore` migrated, behavioral parity confirmed
- `question-store-feasibility` — `LealoneQuestionStore` migrated, feasibility validated
- `orm-jdbc-coexistence` — ORM Stores and `LealonePolicyStore` (raw JDBC) coexist on a shared DB

All the evidence needed to write durable adoption requirements now exists. Without
these requirements in spec form, M32's platform-unification work would start
without a shared understanding of the DB capability domain's boundaries. M32 is
blocked on M31 completing, and this change is the last M31 item.

## Scope

In scope:

- New ORM adoption spec (`orm/orm-adoption.md`) that captures the admission
  criteria, applicable scenarios, interface-preservation contract,
  escape-hatch rules, and coexistence contract as observable requirements
- A focused JUnit test (`OrmAdoptionGuidelinesTest`) that validates the
  escape-hatch invariant: a Store that was deliberately not migrated continues
  to operate correctly alongside the ORM-backed Stores

Out of scope:

- Migrating any additional Stores to ORM
- Changing any existing Store public interface or table schema
- Introducing a generic ORM repository or transaction manager
- Modifying `LealoneDeliveryLogStore`, `LealoneQuestionStore`, or
  `LealonePolicyStore` behavior
- Designing the M32 platform layer

## Unchanged Behavior

- `LealoneDeliveryLogStore`, `LealoneQuestionStore`, `LealonePolicyStore`, and
  all other existing Stores retain their current public interfaces and behavior
- No table schemas change
- No existing tests are modified to satisfy this change
