# Design: orm-jdbc-coexistence

## Approach

Treat coexistence as behavioral evidence, not as a new data-access framework.
Review the existing ORM-backed question-domain Stores and one simple raw
JDBC-backed Store before implementation. The current repository evidence points
to `LealoneDeliveryLogStore`, `LealoneQuestionStore`, and
`LealonePolicyStore` as a focused set: two Stores exercise the completed ORM
pilot paths, and one Store exercises an established raw JDBC path with its own
tables.

Add a focused JUnit test that creates one fresh embedded Lealone database URL
and constructs all participating Stores with that same URL. The test should
interleave operations through public Store APIs, then read results back through
those same APIs. It should also retain the ORM table-boundary evidence by
checking that Store-written question and delivery-log rows remain visible in the
existing tables while the raw JDBC Store has initialized and written its own
tables.

If production code changes are needed, keep them local to the participating
Stores and only to preserve the already-specified behavior. The expected path is
that this change mainly adds coexistence tests and only adjusts Store
initialization or query behavior if those tests expose a real conflict.

## Key Decisions

- Use a shared embedded Lealone JDBC URL in tests. Coexistence must be proven in
  one database instance, not by isolated Store unit tests.
- Use `LealonePolicyStore` as the representative raw JDBC Store because it has a
  small public contract, owns independent permission tables, and does not depend
  on unrelated runtime services.
- Keep coexistence checks at public Store and SQL table boundaries. Tests should
  not assert private ORM internals.
- Preserve Store-owned table initialization. This keeps the existing embedded DB
  setup model intact and avoids introducing migration tooling in M31.
- Do not introduce a transaction manager. Existing Stores use short-lived
  Lealone connections and observable committed results; broader lifecycle
  coordination belongs to later platform work if needed.
- Do not write the final adoption guidelines here. This change produces the
  evidence that the next roadmap item should summarize.

## Alternatives Considered

- Start `orm-adoption-guidelines` first: rejected because the guidelines should
  rely on coexistence evidence from an actual shared-database test.
- Start M32 `lealone-platform-core`: rejected because M32 depends on completing
  M31's DB/ORM direction.
- Migrate another raw JDBC Store to ORM: rejected because this roadmap item is
  about coexistence, not expanding the migration set.
- Test every raw JDBC Store against every ORM Store: rejected as unnecessarily
  broad for the first coexistence proof. One representative raw JDBC Store plus
  both current ORM-backed Stores gives enough evidence without turning this into
  exhaustive matrix testing.
- Add a generic repository or transaction abstraction: rejected as speculative
  and contrary to M31's controlled-adoption goal.
- Move table initialization into a shared schema manager: rejected because it is
  platform/lifecycle work and would exceed this change's scope.
