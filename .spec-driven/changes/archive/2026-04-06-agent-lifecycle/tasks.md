# Tasks: agent-lifecycle

## Implementation

- [x] 实现 `DefaultAgent` 类，实现 `Agent` 接口，内部使用 `volatile AgentState` 跟踪状态
- [x] 实现 `transitionTo(AgentState target)` 私有方法，校验转换合法性，非法时抛出 `IllegalStateException`
- [x] 实现 `init(Map<String, String> config)` — 存储 config，状态设为 IDLE
- [x] 实现 `start()` — IDLE → RUNNING
- [x] 实现 `execute(AgentContext)` — 仅在 RUNNING 状态可执行，异常时自动转 ERROR
- [x] 实现 `stop()` — RUNNING/PAUSED/ERROR → STOPPED
- [x] 实现 `close()` — 从任意状态可调用，释放资源，最终状态为 STOPPED
- [x] 实现 `getState()` — 返回当前 volatile 状态

## Testing

- [x] `mvn test` passes — lint and validation (153 tests, 0 failures)
- [x] `DefaultAgentTest.java` — unit tests covering all valid and invalid state transitions (23 tests)
- [x] 测试合法转换：IDLE→RUNNING, RUNNING→STOPPED, RUNNING→PAUSED, PAUSED→RUNNING, PAUSED→STOPPED, ERROR→STOPPED
- [x] 测试非法转换抛出 IllegalStateException（如 IDLE→STOPPED, IDLE→PAUSED, STOPPED→RUNNING）
- [x] 测试 init 设置 config 并进入 IDLE
- [x] 测试 start 从 IDLE 进入 RUNNING
- [x] 测试 execute 仅在 RUNNING 状态可调用
- [x] 测试 execute 异常时状态自动转为 ERROR
- [x] 测试 close 可从任意状态调用
- [x] 测试 stop 可从 RUNNING/PAUSED/ERROR 调用

## Verification

- [x] 所有测试通过
- [x] DefaultAgent 实现符合 Agent 接口契约
- [x] 状态转换规则与 agent-interface.md spec 一致
