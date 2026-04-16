# Questions: service-http-exposure

## Open

<!-- No open questions -->

## Resolved

- [x] Q: What HTTP namespace should application service exposure use?
  Context: M36 requires application services to coexist with the existing `/api/v1/*` agent API without route ambiguity.
  A: Use `POST /services/{serviceName}/{methodName}` for the first application-service invocation namespace.
- [x] Q: What request body format should the first invocation contract support?
  Context: The proposal needs a stable, testable method argument binding contract without supporting every possible shape immediately.
  A: Support a JSON object with positional arguments: `{"args":[...]}`.
- [x] Q: Should the first service HTTP exposure path use the existing HTTP authentication boundary?
  Context: Exposed application services are externally callable and must not accidentally bypass the repository's established HTTP API security model.
  A: Yes. The first service HTTP exposure path uses the existing HTTP authentication/filter chain by default.
