# Questions: workflow-runtime-contract

## Open

<!-- No open questions -->

## Resolved

- [x] Q: Which public surfaces should the first workflow runtime contract cover?
  Context: The proposal scope changes materially depending on whether the first workflow contract stays SDK-local or defines transport parity immediately.
  A: Cover SDK, HTTP REST, and JSON-RPC in the first proposal.
- [x] Q: What should count as the first supported workflow declaration form?
  Context: The first workflow contract could start with only an in-process domain model or also commit to a Lealone SQL declaration path.
  A: Cover both the domain contract and the first supported Lealone SQL declaration path.
