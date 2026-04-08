# Questions: jsonrpc-protocol

## Open

<!-- No open questions -->

## Resolved

- [x] Q: Should the JSON-RPC protocol use Lealone's `JsonObject`, the project's own `JsonReader`/`JsonWriter`, or add an external JSON library?
  Context: Determines dependency footprint and reuse strategy for the protocol layer.
  A: Use the existing `JsonReader`/`JsonWriter` from `org.specdriven.agent.json` — zero new dependencies, consistent with LLM clients and MCP transport that already use these utilities.

- [x] Q: Should `params` in `JsonRpcRequest` be typed as `Map<String, Object>` or `Object`?
  Context: JSON-RPC 2.0 allows both by-name (object/Map) and by-position (array/List) params.
  A: Type `params` as `Object` to handle both forms. The codec decodes to `Map<String, Object>` for by-name and `List<Object>` for by-position. Callers can `instanceof` check.
