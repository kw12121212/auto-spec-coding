# Lealone Alignment

## Verified Baseline

- Upstream repository: `https://github.com/lealone/Lealone`
- Verified commit: `16259183819d97b42210df9be6763f5a387fe79e`
- Verified artifact line: `8.0.0-SNAPSHOT`
- Repo-local install workflow: `./scripts/install-lealone-upstream.sh`

## Checked Areas

- Compiler integration: adapted `LealoneSkillSourceCompiler` to use Lealone's public `SourceCompiler` API instead of reflecting into private compiler internals.
- Service executor SPI: `SkillServiceExecutorFactory` remains compatible with the current `ServiceExecutorFactory` and `ServiceExecutorFactoryBase` contracts from upstream Lealone.
- Embedded JDBC stores and JSON helpers: current `jdbc:lealone:embed:` store implementations and `com.lealone.orm.json` usage were kept unchanged and are covered by focused regression tests in this change.

## Verification Workflow

```bash
LEALONE_SRC_DIR="$PWD/lealone" LEALONE_SKIP_FETCH=true ./scripts/install-lealone-upstream.sh
mvn -q -DskipTests compile
mvn -q -Dtest=SkillSourceCompilerTest,SkillServiceExecutorFactoryTest,LealoneSessionStoreTest,LealoneRuntimeLlmConfigStoreTest,LealoneToolCacheTest,LealoneQuestionStoreTest,LealoneVaultTest,LealoneTaskStoreTest,LealoneTeamStoreTest,LealoneCronStoreTest test
```

## Future-Useful Upstream Capabilities

- Lealone's public `SourceCompiler` surface is stable enough to use directly for future skill-compilation work without relying on private nested compiler classes.
- The service executor plugin surface remains small and explicit, which should help later skill runtime work stay aligned with upstream SPI expectations.
- Upstream `Service` and compiler integration points reinforce the roadmap direction toward a thin Lealone-centered platform layer, but that broader M32 work remains out of scope for this change.
