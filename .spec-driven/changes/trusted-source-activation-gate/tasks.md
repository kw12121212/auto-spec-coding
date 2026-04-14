# Tasks: trusted-source-activation-gate

## Implementation

- [ ] Update the `skill-hot-loader` delta spec with trusted-source policy requirements, trust-failure behavior, and no-side-effect guarantees for `load` and `replace`
- [ ] Update the `skill-auto-discovery` delta spec so discovery reports trusted-source rejection as a per-skill hot-load failure without changing SQL registration behavior
- [ ] Add a visible hot-load trusted-source failure type for untrusted source and missing trust policy rejection
- [ ] Add a minimal programmatic trusted-source policy contract keyed by `skillName + sourceHash`
- [ ] Enforce trusted-source checks in enabled `load` and `replace` after permission checks and before compile/cache/registry side effects
- [ ] Preserve disabled activation, permission rejection, duplicate load, failed compile, cache-hit, and class-loader isolation behavior

## Testing

- [ ] Add unit tests for trusted `load` and `replace` preserving existing enabled behavior
- [ ] Add unit tests for untrusted `load` and `replace` producing visible failure before compile/cache/registry side effects
- [ ] Add unit tests for missing trusted-source policy failing closed when activation is enabled
- [ ] Add discovery tests for trusted-source rejection while preserving SQL registration counts and continued processing
- [ ] Run validation build with `mvn -q -DskipTests compile`
- [ ] Run unit tests with `mvn -q -Dtest=SkillHotLoaderTest,SkillAutoDiscoveryTest test`

## Verification

- [ ] Run `node /home/wx766/.agents/skills/roadmap-recommend/scripts/spec-driven.js verify trusted-source-activation-gate`
- [ ] Verify implementation matches the proposal scope and keeps unrelated M34 audit logging out of scope
