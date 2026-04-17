# standalone-runtime-jar

## What

Produce a single packaged runtime jar that operators can deliver and start with `java -jar` without copying a sibling `target/dependency/` directory or keeping the repository checkout on disk.

Update the packaged-runtime documentation to point at the self-contained jar, including the existing service runtime entrypoint arguments.

## Why

The current packaged runtime flow is still a thin jar plus copied dependencies on the filesystem. That is not a production-friendly handoff artifact and does not satisfy a "deliver one jar" deployment path.

The runtime also defaults some bundled behavior, such as Sandlock discovery, from repository-relative files. A packaged runtime jar must keep working when those repository files are absent.

## Scope

In scope:
- Maven packaging changes required to build a self-contained runtime jar
- Runtime fallback needed so bundled Sandlock files remain available when running from the packaged jar outside the repository checkout
- Documentation and tests covering the supported packaged-runtime path

Out of scope:
- New installation scripts, system packages, service managers, or container images
- Changing the existing CLI surface or service-runtime arguments
- Broad release workflow changes beyond the standalone jar path

## Unchanged Behavior

Behaviors that must not change as a result of this change (leave blank if nothing is at risk):
- Existing SDK, JSON-RPC, and HTTP API behavior
- Existing service-runtime startup semantics and structured JSON output
- Explicit `SPEC_DRIVEN_SANDLOCK_ENTRY` override precedence over bundled defaults
