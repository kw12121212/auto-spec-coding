# Questions: sdk-public-api

## Open

<!-- No open questions -->

## Resolved

- [x] Q: Facade entry pattern — single SpecDriven class vs multiple entry classes?
  Context: Determines API discoverability and surface area
  A: Single `SpecDriven` facade with builder pattern. Confirmed by user.

- [x] Q: Package structure — dedicated `org.specdriven.sdk` vs existing `org.specdriven.agent`?
  Context: Determines public/internal API boundary
  A: Dedicated `org.specdriven.sdk` package. Re-export shared value types from original packages. Confirmed by user.

- [x] Q: LLM provider auto-assembly — SDK auto-assembles from config or user manually assembles?
  Context: Affects ease-of-use vs flexibility tradeoff
  A: Auto-assembly as default via `.config(Path)`, manual override via `.providerRegistry()`. Manual takes precedence when both set. Confirmed by user.
