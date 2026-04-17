# Design: standalone-runtime-jar

## Approach

Attach a second Maven artifact with the `standalone` classifier using `maven-shade-plugin`. The standalone jar will include runtime dependencies, a `Main-Class` manifest entry for `org.specdriven.cli.SpecDrivenCliMain`, and merged service descriptors so it can run with `java -jar`.

Add the repository-bundled Sandlock files under jar resources and teach `LealonePlatform.SystemSandlockRuntime` to resolve them in this order:
1. `SPEC_DRIVEN_SANDLOCK_ENTRY`
2. repository `depends/sandlock/...`
3. packaged jar resources extracted to a local runtime cache directory

Update runtime docs to describe the standalone artifact and add tests for the Sandlock packaged-resource fallback.

## Key Decisions

- Keep the existing thin jar for SDK/library consumption and attach a second standalone artifact instead of replacing the main Maven artifact.
- Use shade rather than `dependency:copy-dependencies` so operators receive one file instead of a jar plus directory tree.
- Extract packaged Sandlock resources to a stable local cache directory before execution so native binaries and shared libraries can be executed with normal filesystem semantics.

## Alternatives Considered

- Requiring `dependency:copy-dependencies` in production was rejected because it still depends on extra files next to the jar.
- Replacing the main artifact with a fat jar was rejected because this repository also publishes a Java SDK/library surface.
- Keeping Sandlock repository-relative only was rejected because a delivered standalone jar may run outside the source checkout.
