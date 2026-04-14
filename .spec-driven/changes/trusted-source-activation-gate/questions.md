# Questions: trusted-source-activation-gate

## Open

<!-- No open questions -->

## Resolved

- [x] Q: What should count as a trusted source for `trusted-source-activation-gate`?
  Context: The roadmap requires trusted-source validation before activation, but did not define whether trust means an allowed skill directory, allowed source hash, requester/policy constraint, or another contract.
  A: Use a minimal local/programmatic trust model keyed by `skillName + sourceHash`; discovery may carry source path only as context, and this change must not add code signing, remote trust registries, or CLI/HTTP external configuration surfaces.
