# Tasks: sdk-public-api

## Implementation

- [x] Create `org.specdriven.sdk.SdkConfig` record with maxTurns, toolTimeoutSeconds, systemPrompt fields and `defaults()` factory
- [x] Create `org.specdriven.sdk.SdkException` extending RuntimeException with cause-preserving constructor
- [x] Create `org.specdriven.sdk.SdkBuilder` with methods: config(Path), providerRegistry(LlmProviderRegistry), registerTool(Tool), systemPrompt(String), sdkConfig(SdkConfig), build()
- [x] Create `org.specdriven.sdk.SdkAgent` wrapping DefaultAgent lifecycle: run(String), stop(), getState()
- [x] Create `org.specdriven.sdk.SpecDriven` with static builder() returning SdkBuilder, and close() method
- [x] Implement auto-assembly in SdkBuilder.build(): config loading → vault resolution → provider registry creation → tool registration
- [x] Implement SdkAgent.run(): create Conversation, append SystemMessage if configured, append UserMessage, create AgentContext, delegate to DefaultAgent lifecycle (init/start/execute/stop)

## Testing

- [x] Lint: run `mvn compile -q` to verify all new code compiles without errors
- [x] Write unit test: `SdkConfigTest` — verify defaults(), custom constructor, immutability
- [x] Write unit test: `SdkExceptionTest` — verify message and cause preservation
- [x] Write unit test: `SdkBuilderTest` — verify builder with config file, manual registry, tool registration, precedence rules
- [x] Write unit test: `SdkAgentTest` — verify run() lifecycle, stop(), error wrapping (using mock LlmClient)
- [x] Write unit test: `SpecDrivenTest` — verify builder() factory, close() releases resources, end-to-end with mock provider
- [x] Unit tests: run `mvn test -pl . -Dtest="SdkConfigTest,SdkExceptionTest,SdkBuilderTest,SdkAgentTest,SpecDrivenTest"`

## Verification

- [x] Verify implementation matches proposal scope
- [x] Verify no internal types are exposed through SDK public API (all return types are in `org.specdriven.sdk` or shared value types)
- [x] Verify auto-assembly works with existing YAML config format used by `DefaultLlmProviderRegistry.fromConfig`
