# Tasks: delivery-log-orm-pilot

## Implementation

- [x] Review `DeliveryLogModel`, `LealoneDeliveryLogStore`, and the archived
  `orm-model-mappings` change before editing.
- [x] Ensure `LealoneDeliveryLogStore.save`, `findByQuestion`, and
  `findLatestByQuestion` use the delivery-log mapped storage path where
  practical while preserving current public behavior.
- [x] Preserve `DeliveryLogStore`, `DeliveryAttempt`,
  `LealoneDeliveryLogStore` construction, and the existing `delivery_log` table
  shape.
- [x] Add a Store-to-table interoperability check proving Store-saved attempts
  are visible through the existing `delivery_log` columns.
- [x] Keep all non-delivery-log Stores on their existing raw JDBC paths.
- [x] Do not introduce a generic ORM repository/base Store abstraction.

## Testing

- [x] Add or update focused JUnit tests for Store behavior parity, raw SQL
  interoperability, existing row readability, ordering, latest lookup, empty
  lookup, and nullable fields.
- [x] Run validation command `node /home/wx766/.agents/skills/roadmap-recommend/scripts/spec-driven.js verify delivery-log-orm-pilot`.
- [x] Run validation command `mvn -q -DskipTests compile`.
- [x] Run focused unit test command `mvn test -q -Dtest=LealoneDeliveryLogStoreTest -Dsurefire.useFile=false`.

## Verification

- [x] Confirm the implementation satisfies the delta spec and stays limited to
  the delivery-log pilot.
- [x] Confirm public Store APIs and delivery-log observable behavior remain
  unchanged.
- [x] Confirm no `LealoneQuestionStore` migration, broad ORM guideline, or
  generic repository abstraction was added in this change.
