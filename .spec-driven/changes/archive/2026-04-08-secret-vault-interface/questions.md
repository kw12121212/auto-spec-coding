# Questions: secret-vault-interface

## Open

<!-- No open questions — all resolved below -->

## Resolved

- [x] Q: AES-256-GCM nonce strategy?
  Context: Nonce management affects security and implementation complexity.
  A: Random per-encryption — 12-byte random nonce stored alongside ciphertext.

- [x] Q: Environment variable name for master key?
  Context: Needs a clear, namespaced name that won't collide with other tools.
  A: `SPEC_DRIVEN_MASTER_KEY`

- [x] Q: Should vault entries support metadata?
  Context: Metadata adds complexity but is useful for audit and rotation.
  A: Yes — creation timestamp and description per entry.

- [x] Q: How to handle development environments without setting the master key?
  Context: Developers shouldn't need to configure the env var for local dev.
  A: Fixed default master key for dev. Warning logged when default is active. Production must set the env var explicitly.
