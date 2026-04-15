# Tasks: orm-model-mappings

## Implementation

- [x] Review the Lealone ORM `Model` API and the current `LealoneDeliveryLogStore` table, save, and query behavior before editing.
- [x] Add the minimum delivery-log ORM model mapping needed to represent the existing `delivery_log` columns.
- [x] Adapt `LealoneDeliveryLogStore` so `save`, `findByQuestion`, and `findLatestByQuestion` preserve the existing `DeliveryLogStore` contract through the mapped storage path.
- [x] Keep `DeliveryLogStore`, `DeliveryAttempt`, existing constructor behavior, and non-delivery-log Stores unchanged.
- [x] Avoid introducing a generic ORM repository/base Store abstraction in this change.

## Testing

- [x] Add or update focused JUnit tests for delivery-log ORM mapping compatibility, field round-trip behavior, existing table row readability, ordering, latest lookup, and nullable fields.
- [x] Run validation command `node /home/wx766/.agents/skills/roadmap-recommend/scripts/spec-driven.js verify orm-model-mappings`.
- [x] Run validation command `mvn -q -DskipTests compile`.
- [x] Run focused unit test command `mvn test -q -Dtest=LealoneDeliveryLogStoreTest -Dsurefire.useFile=false`.

## Verification

- [x] Confirm the implementation matches the delta spec and does not expand into `delivery-log-orm-pilot`.
- [x] Confirm public Store APIs and current delivery-log behavior remain unchanged.
- [x] Confirm no other Lealone-backed Store was migrated or refactored as part of this change.
