# Design: orm-adoption-guidelines

## Approach

Treat the M31 pilot archives as the authoritative evidence base. Review
`orm-model-mappings`, `delivery-log-orm-pilot`, `question-store-feasibility`,
and `orm-jdbc-coexistence` to identify the observable invariants that held
across all pilots, then encode those as ADDED requirements in a new
`orm/orm-adoption.md` spec file.

The spec should define four requirement areas:
1. **Admission criteria** — the observable conditions that make a Store a good
   candidate for ORM migration (bounded table, simple CRUD surface, no complex
   joins, no raw SQL complexity that ORM cannot express naturally)
2. **Interface preservation** — migrated Stores MUST retain their public method
   signatures and return types unchanged
3. **Escape-hatch rule** — Stores that do not meet the admission criteria MUST
   remain as raw JDBC; the absence of migration IS the escape hatch
4. **Coexistence contract** — ORM-backed and raw-JDBC-backed Stores MUST
   initialize and operate correctly against the same embedded Lealone JDBC URL

For the test, add `OrmAdoptionGuidelinesTest` in the question domain test
package. It should initialize all three participating Stores
(`LealoneDeliveryLogStore`, `LealoneQuestionStore`, `LealonePolicyStore`) with
a single shared fresh embedded Lealone URL, perform a write and a read through
each Store's public API, and assert that each Store's results are correct. This
test documents the escape-hatch invariant: `LealonePolicyStore` was
deliberately not migrated and MUST still function normally alongside the ORM
Stores. It complements `OrmJdbcCoexistenceTest` by anchoring the guidelines
spec to a named, intent-labelled test class.

## Key Decisions

- The spec is the primary deliverable. No production code changes are
  expected; the pilots already built everything needed.
- Keep the test class name explicit (`OrmAdoptionGuidelinesTest`) so it reads
  as living documentation of the adoption policy, distinct from the general
  coexistence proof in `OrmJdbcCoexistenceTest`.
- Do not add complex assertion logic or new Store interactions beyond a
  single write + read per Store. The test's purpose is to document the
  boundary, not to retest behavior already covered by existing Store tests.
- Scope the escape hatch definition to "don't migrate" rather than inventing
  a per-method fallback mechanism. The pilot evidence shows no such mechanism
  is needed for the current Store set; adding one would be speculative.
- Mirror `orm/orm-adoption.md` into the main `.spec-driven/specs/` directory
  only after this change is archived, so the main spec reflects what was
  actually implemented.

## Alternatives Considered

- Write the admission criteria as a prose AGENTS.md section instead of a spec:
  rejected because spec requirements are observable and independently
  verifiable; prose guidelines are not.
- Skip the new test and rely on `OrmJdbcCoexistenceTest` alone: rejected
  because that test does not signal intent around the escape-hatch decision; a
  named `OrmAdoptionGuidelinesTest` makes the policy traceable.
- Add a per-method raw JDBC fallback inside ORM Stores: rejected as speculative.
  The pilots show no scenario where a migrated Store needed a within-Store
  fallback.
- Start M32 `lealone-platform-core` before closing M31: rejected because M32
  explicitly depends on M31 completing, and the coexistence contract in this
  spec is direct planning input for M32's DB capability domain.
