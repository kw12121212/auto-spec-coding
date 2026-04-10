# Question Resolution Spec Delta

## ADDED Requirements

### Requirement: ReplyCallbackRouter

The system MUST provide a `ReplyCallbackRouter` in `org.specdriven.agent.question` that maps channel type names to assembled `QuestionReplyCollector` instances and dispatches incoming callback payloads.

#### Scenario: Register collector by channel type
- GIVEN a `ReplyCallbackRouter` instance
- WHEN `register("telegram", collector)` is called
- THEN subsequent dispatches for "telegram" MUST route to that collector

#### Scenario: Reject duplicate channel type registration
- GIVEN a router with a collector registered for "telegram"
- WHEN `register("telegram", otherCollector)` is called
- THEN it MUST throw `IllegalArgumentException`

#### Scenario: Dispatch valid Telegram callback
- GIVEN a router with a registered Telegram collector and webhook secret
- AND an incoming payload with a valid `X-Telegram-Bot-Api-Secret-Token` header
- WHEN `dispatch("telegram", payload, headers)` is called
- THEN the payload MUST be forwarded to the registered Telegram collector's `processCallback(payload)`

#### Scenario: Reject Telegram callback with invalid secret
- GIVEN a router with a registered Telegram collector and expected webhook secret
- AND an incoming payload with a mismatched or missing `X-Telegram-Bot-Api-Secret-Token` header
- WHEN `dispatch("telegram", payload, headers)` is called
- THEN it MUST throw `MobileAdapterException` with channel type "telegram"

#### Scenario: Dispatch valid Discord callback
- GIVEN a router with a registered Discord collector
- AND an incoming payload with an `X-Signature-256` header
- WHEN `dispatch("discord", payload, headers)` is called
- THEN the payload and signature MUST be forwarded to the registered Discord collector's `processCallback(payload, signature)`

#### Scenario: Reject unknown channel type
- GIVEN a router with no collector registered for "slack"
- WHEN `dispatch("slack", payload, headers)` is called
- THEN it MUST throw `IllegalArgumentException` identifying the unknown channel type

#### Scenario: List registered channel types
- GIVEN a router with collectors registered for "telegram" and "discord"
- WHEN `registeredChannels()` is called
- THEN it MUST return a set containing "telegram" and "discord"

### Requirement: Callback HTTP endpoint

The system MUST expose HTTP callback endpoints that receive webhook payloads from external mobile channels and dispatch them through the `ReplyCallbackRouter`.

#### Scenario: Telegram callback endpoint
- GIVEN a running HTTP server with a registered Telegram collector in the router
- WHEN a `POST /api/v1/callbacks/telegram` request arrives with a valid JSON body and correct `X-Telegram-Bot-Api-Secret-Token` header
- THEN the server MUST respond with HTTP 200
- AND the body MUST be dispatched through the router to the Telegram collector

#### Scenario: Discord callback endpoint
- GIVEN a running HTTP server with a registered Discord collector in the router
- WHEN a `POST /api/v1/callbacks/discord` request arrives with a valid JSON body and `X-Signature-256` header
- THEN the server MUST respond with HTTP 200
- AND the body and signature MUST be dispatched through the router to the Discord collector

#### Scenario: Callback endpoint bypasses auth
- GIVEN a running HTTP server with auth middleware
- WHEN a `POST /api/v1/callbacks/{channelType}` request arrives without an auth token
- THEN the request MUST still be processed (not rejected by auth middleware)

#### Scenario: Unknown channel type returns 404
- GIVEN a running HTTP server
- WHEN a `POST /api/v1/callbacks/unknown` request arrives
- THEN the server MUST respond with HTTP 404

#### Scenario: Invalid Telegram secret returns 401
- GIVEN a running HTTP server with a registered Telegram collector
- WHEN a `POST /api/v1/callbacks/telegram` request arrives with an invalid secret token header
- THEN the server MUST respond with HTTP 401

#### Scenario: Callback error returns structured error
- GIVEN a running HTTP server
- WHEN a callback dispatch causes an exception
- THEN the server MUST respond with an appropriate HTTP error code and a structured JSON error body
