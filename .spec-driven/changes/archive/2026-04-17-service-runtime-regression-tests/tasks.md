# Tasks: service-runtime-regression-tests

## Implementation

- [x] Review current service runtime, service HTTP exposure, and CLI runtime tests to identify missing regression scenarios against the existing specs.
- [x] Extend runtime-focused tests to cover startup happy path, configuration validation failures, and structured bootstrap or startup error propagation where coverage is currently missing.
- [x] Extend service HTTP runtime tests to cover packaged runtime namespace availability, auth boundary preservation, and failure-path behavior where coverage is currently missing.
- [x] Make only the smallest local test-fixture or assertion-helper changes needed to support the added regression cases.

## Testing

- [x] Run validation build `mvnd compile -q`.
- [x] Run targeted unit tests `mvnd -q -Dtest=ServiceRuntimeLauncherTest,HttpE2eTest,SpecDrivenCliMainTest test -Dsurefire.useFile=false`.
- [x] Run `mvnd test -q -Dsurefire.useFile=false`.

## Verification

- [x] Verify the added tests map only to already-specified observable behavior in `release/service-runtime-packaging.md` and `api/service-http-exposure.md`.
- [x] Verify the change does not broaden scope into M40-style fixture standardization or general quality-gate work.
