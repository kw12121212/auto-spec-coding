---
mapping:
  implementation:
    - src/main/java/org/specdriven/agent/question/DeliveryLogModel.java
    - src/main/java/org/specdriven/agent/question/LealoneDeliveryLogStore.java
  tests:
    - src/test/java/org/specdriven/agent/question/LealoneDeliveryLogStoreTest.java
---

# Question Resolution - Delta Spec: orm-model-mappings

## ADDED Requirements

### Requirement: Delivery log storage mapping compatibility

The system MUST define a delivery-log storage mapping that preserves the current
`DeliveryLogStore` behavior against the existing `delivery_log` table.

#### Scenario: Delivery attempt fields round-trip through mapped storage

- GIVEN a `LealoneDeliveryLogStore` backed by a fresh database
- WHEN a `DeliveryAttempt` is saved and then read by question ID
- THEN the returned attempt MUST preserve `questionId`, `channelType`,
  `attemptNumber`, `status`, `statusCode`, `errorMessage`, and `attemptedAt`

#### Scenario: Nullable delivery attempt fields remain supported

- GIVEN a `DeliveryAttempt` with a null `statusCode` or null `errorMessage`
- WHEN the attempt is saved and read back
- THEN the corresponding returned field MUST remain null

#### Scenario: Existing delivery log table rows remain readable

- GIVEN a row compatible with the existing `delivery_log` table columns
- WHEN `findByQuestion(questionId)` is called for that row's question
- THEN the row MUST be returned as a `DeliveryAttempt`

#### Scenario: Delivery log lookup ordering remains stable

- GIVEN multiple delivery attempts for the same question
- WHEN `findByQuestion(questionId)` is called
- THEN attempts MUST be returned in ascending `attemptNumber` order

#### Scenario: Latest delivery attempt lookup remains stable

- GIVEN multiple delivery attempts for the same question
- WHEN `findLatestByQuestion(questionId)` is called
- THEN the returned attempt MUST be the one with the highest `attemptNumber`

#### Scenario: Delivery log public contract remains unchanged

- GIVEN callers use the existing `DeliveryLogStore` methods
- WHEN delivery-log storage mapping is introduced
- THEN callers MUST NOT need new method signatures, new input fields, or a new
  store construction contract
