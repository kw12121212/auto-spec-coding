# Questions: question-contract-audit

## Open

<!-- No open questions -->

## Resolved

- [x] Q: Should `question-contract-audit` include SDK-facing types and query/submit APIs?
  Context: This determines whether the proposal changes the public SDK surface or stays at the contract/audit foundation layer.
  A: No. SDK-facing pending-question and manual-answer APIs are deferred to `question-delivery-surface`.
- [x] Q: Should `confidence` be required in the base answer contract?
  Context: This determines the minimum observable answer payload and audit traceability for later runtime work.
  A: Yes. `confidence` is required in the base answer contract.
