# Simple Task: update-lealone-source

## Task

Update the checked-out Lealone source to the latest upstream `master` commit, using proxy `http://127.0.0.1:8118` for network access.

## What was done

- Confirmed upstream `https://github.com/lealone/Lealone` `master` currently resolves to `16259183819d97b42210df9be6763f5a387fe79e`.
- Updated the `lealone` submodule worktree to `16259183819d97b42210df9be6763f5a387fe79e`.
- Updated the repo-local Lealone baseline records in `pom.xml`, `README.md`, and `LEALONE_ALIGNMENT.md`.
- Updated `scripts/install-lealone-upstream.sh` to support a checked-out submodule source directory whose `.git` is a file, and to allow `LEALONE_SKIP_FETCH=true` when the target source checkout has already been fetched.
- Installed the updated Lealone `8.0.0-SNAPSHOT` artifacts from the local submodule checkout.
- Ran `mvn -q -DskipTests compile`.
- Ran `mvn -q -Dtest=SkillSourceCompilerTest,SkillServiceExecutorFactoryTest,LealoneSessionStoreTest,LealoneRuntimeLlmConfigStoreTest,LealoneToolCacheTest,LealoneQuestionStoreTest,LealoneVaultTest,LealoneTaskStoreTest,LealoneTeamStoreTest,LealoneCronStoreTest test`.

## Spec impact

None. This updates the existing release-preparation baseline evidence and mapped release files; no observable product requirement changed.

## Follow-up

None.
