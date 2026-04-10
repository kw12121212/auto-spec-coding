# Questions: builtin-mobile-adapters

## Open

<!-- No open questions -->

## Resolved

- [x] Q: Which specific channels should be supported in the first release?
  Context: Determines the adapter implementation scope and which external APIs to integrate.
  A: Telegram and Discord — they represent two distinct messaging patterns and cover the most common use cases.

- [x] Q: Should adapters support channel-specific rich message formats or just plain text?
  Context: Affects adapter SPI design, template layer complexity, and per-adapter code surface.
  A: Plain text only for v1, with a `RichMessageFormatter` extension point for future channel-specific formatting.
