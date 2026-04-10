# Tasks: mobile-reply-callbacks

## Implementation

- [x] Create `ReplyCallbackRouter` in `org.specdriven.agent.question` with `register(channelType, collector, webhookSecret)`, `dispatch(channelType, payload, headers)`, `registeredChannels()`
- [x] Add Telegram `X-Telegram-Bot-Api-Secret-Token` header verification in `ReplyCallbackRouter.dispatch()` — compare against the stored webhook secret resolved from vault
- [x] Add `POST /api/v1/callbacks/{channelType}` route group to `HttpApiServlet` — read body + headers, call `ReplyCallbackRouter.dispatch()`, return 200/401/404
- [x] Bypass auth middleware for `/api/v1/callbacks/*` path prefix — skip `AuthFilter` and `RateLimitFilter` for callback routes
- [x] Wire `ReplyCallbackRouter` into `BuiltinMobileAdapters.registerAll()` flow — after assembling handles, register each handle's collector with the router using vault-resolved webhook secret
- [x] Add `ReplyCallbackRouter` dependency injection to `HttpApiServlet` constructor

## Testing

- [x] Lint/validation: run `mvn compile -pl . -q`
- [x] Run `mvn test -pl . -Dtest=ReplyCallbackRouterTest -q` — unit tests for router registration, dispatch, signature verification, error cases
- [x] Run `mvn test -pl . -Dtest=HttpCallbackEndpointTest -q` — unit tests for callback HTTP routes (Telegram and Discord dispatch, auth bypass, error responses)

## Verification

- [x] Verify all new spec scenarios are covered by tests
- [x] Verify existing delivery channel and reply collector tests still pass
- [x] Verify `mvn test -pl . -q` (full suite) passes with no regressions
