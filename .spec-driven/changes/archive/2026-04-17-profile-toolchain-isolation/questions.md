# Questions: profile-toolchain-isolation

## Open

<!-- No open questions -->

## Resolved

- [x] Q: Should the first isolation contract cover JDK, Node.js, Go, and
  Python, or only the stacks the repository uses most directly today?
  Context: This determines the scope of the delta specs and the intended test
  surface for M38 isolation behavior.
  A: Cover JDK, Node.js, Go, and Python in the first contract, while keeping
  each family's required fields minimal and observable.

- [x] Q: Which cache boundaries belong in the first isolation contract?
  Context: This determines which profile settings and runtime behaviors must be
  part of the initial M38 isolation scope.
  A: Require isolated `HOME` plus explicit cache roots for Maven, npm, Go, and
  pip in the first change; broader tool-specific caches remain out of scope.
