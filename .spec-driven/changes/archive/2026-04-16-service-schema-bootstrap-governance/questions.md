# Questions: service-schema-bootstrap-governance

## Open

<!-- No open questions -->

## Resolved

- [x] Q: Should `service-schema-bootstrap-governance` cover only `services.sql` bootstrap input rules, or also startup runtime config rules such as allowed bind/config overrides?
  Context: The proposal boundary determines whether the change only tightens bootstrap validation or also governs how the runtime launcher assembles startup settings.
  A: Cover both bootstrap governance and startup runtime configuration governance.
