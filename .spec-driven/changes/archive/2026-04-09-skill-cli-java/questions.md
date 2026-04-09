# Questions: skill-cli-java

## Open

<!-- No open questions -->

## Resolved

- [x] Q: Is `skill-cli-java` a per-skill CLI rewrite or a rewrite of the shared `spec-driven.ts` workflow script?
  Context: The proposal originally described the CLI as if it were owned by a single skill. The implementation scope and packaging should instead match the actual shared workflow entrypoint used by multiple skills.
  A: Rewrite the single shared `spec-driven.ts` script. The Java CLI remains one shared workflow tool consumed across skills.
