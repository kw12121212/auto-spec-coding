# tool-lsp — Questions

## Open

<!-- No open questions -->

## Resolved

- [x] Q: Which LSP operations to prioritize?
  Context: Determines implementation scope and effort
  A: Diagnostics (syntax validation) is the primary focus. Supporting operations: hover, goToDefinition, references, documentSymbols.

- [x] Q: JSON-RPC layer coupling with M13?
  Context: Affects code organization and future refactoring cost
  A: Standalone implementation with loose coupling. M13 can extract a shared codec later.
