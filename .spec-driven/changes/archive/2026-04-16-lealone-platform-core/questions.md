# Questions: lealone-platform-core

## Open

<!-- No open questions -->

## Resolved

- [x] Q: Should `LealonePlatform` be introduced as a public SDK-facing entry point in this change?
  Context: This determines whether the proposal modifies the current SDK public contract or keeps the platform surface internal only.
  A: Yes. The change should introduce a public platform entry.

- [x] Q: Should the first platform core expose typed capability accessors or a generic registry?
  Context: This determines the shape of the new platform contract and how future M32 changes build on it.
  A: Use typed capability accessors for the initial platform core.
