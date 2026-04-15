---
mapping:
  implementation:
    - src/main/java/org/specdriven/agent/question/DeliveryLogModel.java
    - src/main/java/org/specdriven/agent/question/LealoneDeliveryLogStore.java
  tests:
    - src/test/java/org/specdriven/agent/question/LealoneDeliveryLogStoreTest.java
---

# Question Resolution - Delta Spec: delivery-log-orm-pilot

## ADDED Requirements

### Requirement: Delivery log table interoperability

The system MUST preserve bidirectional interoperability between
`LealoneDeliveryLogStore` operations and the existing `delivery_log` table
columns.

#### Scenario: Store-saved attempts remain table-visible

- GIVEN a `LealoneDeliveryLogStore` backed by a fresh Lealone database
- WHEN a caller saves a `DeliveryAttempt` through the Store
- THEN a row MUST be visible in the existing `delivery_log` table columns with
  the saved attempt values

#### Scenario: Compatible table rows remain store-readable

- GIVEN a row inserted directly into the existing `delivery_log` table columns
- WHEN `findByQuestion(questionId)` is called for that row's question
- THEN the Store MUST return a matching `DeliveryAttempt`

#### Scenario: Lookup behavior remains stable through the pilot

- GIVEN multiple delivery attempts for the same question
- WHEN callers use `findByQuestion(questionId)` and
  `findLatestByQuestion(questionId)`
- THEN attempts MUST remain ordered by ascending `attemptNumber` for
  `findByQuestion`
- AND latest lookup MUST return the attempt with the highest `attemptNumber`

#### Scenario: Public delivery log API remains unchanged

- GIVEN existing callers construct and use `LealoneDeliveryLogStore`
- WHEN the delivery-log pilot is completed
- THEN callers MUST NOT need new method signatures, new input fields, or a new
  Store construction contract
