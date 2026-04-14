# lealone-agent-adapter

## What

为 M29 引入 `LealoneAgentAdapter`，把 Lealone 的 SQL+自然语言交互入口包装成本项目已经定义好的 `InteractiveSession` 实现。

具体交付内容：
- 新增一个 Lealone-backed `InteractiveSession` 实现，用于接收 SQL/NL 输入并把执行输出放入现有 `drainOutput()` 缓冲语义中
- 明确 adapter 的生命周期行为：启动时建立可用交互连接，活动态接收输入，关闭后释放资源并拒绝后续输入
- 对 SQL/NL 输入执行失败、底层连接失败等情况给出可观察的错误态与输出行为
- 保持 adapter 只负责 Lealone 交互会话，不负责 LoopDriver 暂停恢复、Question/Answer 回写、命令解析或审计
- 增加单元测试覆盖生命周期、输入提交、输出 drain、失败转入 `ERROR`、关闭释放资源等行为

## Why

M29 中 `interactive-session-interface` 已完成，后续 `loop-question-interactive-bridge` 需要一个真实的交互会话实现才能把暂停中的人工处理入口接入自主循环。先实现 `lealone-agent-adapter` 可以把 Lealone SQL/NL 交互细节限制在 adapter 内部，让 loop bridge 和 command parser 继续依赖本项目的 `InteractiveSession` 契约。

这个顺序也能降低状态机风险：本 change 只验证 Lealone 交互能力可以被稳定封装，不同时改变 `DefaultLoopDriver` 的 `QUESTIONING -> PAUSED` 行为，也不提前实现自然语言指令到 `Answer` 的映射。

## Scope

**In scope:**
- 实现 `LealoneAgentAdapter` 作为 `InteractiveSession` 的 Lealone-backed 实现
- 支持通过 adapter 提交 SQL/NL 输入，并按现有 `drainOutput()` 语义读取输出
- 管理 adapter 生命周期、底层资源关闭和错误态
- 补充必要的 Lealone module 依赖配置（如果当前 build 还不能直接编译 adapter）
- 为 adapter 增加 JUnit 5 单元测试，优先使用轻量 fake/controlled Lealone execution seam 验证本项目可观察行为

**Out of scope:**
- 修改 `DefaultLoopDriver` 或让 loop 在人工升级点自动进入交互模式；属于 `loop-question-interactive-bridge`
- 实现受限自然语言命令解析、Question Answer 回写或有限循环控制动作；属于 `interactive-command-parser`
- 实现 `SHOW SERVICES` / `SHOW STATUS` / `SHOW ROADMAP` 聚合命令或交互审计；属于 `interactive-show-audit`
- 新增 HTTP/JSON-RPC 远程交互入口
- 修改 Lealone Agent 上游的 NL-to-SQL 翻译能力

## Unchanged Behavior

Behaviors that must not change as a result of this change (leave blank if nothing is at risk):
- 现有 `InteractiveSession` 接口和 `InMemoryInteractiveSession` 行为保持兼容
- 现有 `DefaultLoopDriver` 的 question 升级、暂停、恢复和 completed-change 记录规则保持不变
- 现有 `QuestionRuntime`、`QuestionDeliveryService`、pending-question 状态和 answer 提交流程保持不变
- 本 change 不把用户输入直接写回等待中的 `Question`，也不绕过既有 Question/Answer 生命周期
