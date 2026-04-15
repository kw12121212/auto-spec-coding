# orm-model-mappings

## What

Define the minimum Lealone ORM model mapping needed for the M31 ORM pilot,
starting with the delivery-log storage domain.

This change prepares `LealoneDeliveryLogStore` for an ORM-backed implementation
while preserving the existing `DeliveryLogStore` public contract and the
existing `delivery_log` table shape. The mapping must support the current
observable behavior: saving delivery attempts, reading attempts by question in
attempt order, reading the latest attempt, preserving nullable status/error
fields, and reading rows that match the current table schema.

## Why

M31 is a controlled ORM adoption milestone. The next implementation step should
not jump directly into broad Store migration or introduce a generic ORM layer
before the project has a concrete mapping contract.

`LealoneDeliveryLogStore` is the roadmap's preferred pilot target because it has
a small CRUD surface, an existing Lealone table, focused tests, and clear
round-trip behavior. Defining the minimal mapping first lowers risk for the
later `delivery-log-orm-pilot` change and gives M32's platform-unification work
a clearer DB/ORM direction.

## Scope

In scope:

- Define the delivery-log ORM mapping behavior against the existing
  `delivery_log` table.
- Preserve the `DeliveryLogStore` interface and `LealoneDeliveryLogStore`
  constructor behavior.
- Ensure all `DeliveryAttempt` fields round-trip through the mapped storage
  path, including nullable `statusCode` and `errorMessage`.
- Ensure rows compatible with the existing `delivery_log` table remain readable.
- Add focused tests that prove ORM mapping compatibility without relying on a
  broad Store migration.

Out of scope:

- Migrating every Lealone-backed Store to ORM.
- Changing `DeliveryLogStore` method signatures or `DeliveryAttempt` fields.
- Introducing a generic repository or wide ORM abstraction layer.
- Migrating `LealoneQuestionStore`; that remains part of the later
  `question-store-feasibility` roadmap item.
- Changing database schema migration tooling or adding a new migration system.

## Unchanged Behavior

Behaviors that must not change as a result of this change (leave blank if nothing is at risk):

- `DeliveryLogStore.save()` still persists one `DeliveryAttempt`.
- `findByQuestion(questionId)` still returns attempts for that question ordered
  by ascending `attemptNumber`.
- `findLatestByQuestion(questionId)` still returns the attempt with the highest
  `attemptNumber` or empty when none exists.
- Unknown questions still return empty results.
- The existing `delivery_log` table remains compatible with current persisted
  rows.
- Existing raw JDBC-backed Stores outside the delivery-log pilot remain
  unchanged.
