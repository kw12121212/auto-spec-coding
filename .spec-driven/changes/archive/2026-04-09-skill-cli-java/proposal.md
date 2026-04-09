# skill-cli-java

## What

Implement a Java-native replacement for the current shared `spec-driven.ts` helper CLI so this repository's spec-driven workflow no longer depends on Node.js. The new CLI will cover the same command surface currently exposed by that single shared workflow script: `propose`, `modify`, `apply`, `verify`, `verify-roadmap`, `roadmap-status`, `archive`, `cancel`, `init`, `run-maintenance`, `migrate`, and `list`.

## Why

M11 is almost complete. The remaining planned item is the rewrite of the one shared `spec-driven.ts` workflow script that multiple skills rely on. The repository already has Java implementations for skill SQL conversion, instruction loading, auto-discovery, and executor wiring; leaving that shared workflow entrypoint in a separate helper script keeps the milestone partially complete and prevents the project from being fully self-hosted in Java.

## Scope

In scope:
- A Java CLI entrypoint that can run the existing spec-driven workflow commands without requiring Node.js
- Command dispatch and filesystem behavior for `propose`, `modify`, `apply`, `verify`, `verify-roadmap`, `roadmap-status`, `archive`, `cancel`, `init`, `run-maintenance`, `migrate`, and `list`
- Output and exit-code behavior for key machine-consumed commands, especially `roadmap-status` and `verify-roadmap`
- Unit tests covering command parsing, scaffold generation, roadmap reporting, and validation failures
- Repo-local invocation and documentation updates needed to run the CLI from Java/Maven

Out of scope:
- Implementing any roadmap item outside `skill-cli-java`
- Changing the meaning of existing `.spec-driven/` artifacts or milestone semantics
- Adding new spec-driven commands beyond the current 12-command parity surface
- Reworking the underlying roadmap/spec file formats beyond what parity requires
- General agent runtime, SDK, HTTP, JSON-RPC, or mobile-question features

## Unchanged Behavior

Behaviors that must not change as a result of this change (leave blank if nothing is at risk):
- Existing `.spec-driven/` file layout, proposal artifact names, and roadmap/spec index conventions must remain compatible
- Existing roadmap status semantics and planned-change status semantics must remain unchanged
- The current Java runtime, SDK surfaces, and skill execution behavior outside the CLI workflow must not change
