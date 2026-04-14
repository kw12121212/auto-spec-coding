# Design: lealone-agent-adapter

## Approach

实现一个窄适配层：对外只暴露 `InteractiveSession`，对内封装 Lealone SQL/NL 执行入口和输出收集。adapter 不新增 loop 控制面，也不理解 question 的业务语义。

建议实现形态：
- 在 `org.specdriven.agent.interactive` 下新增 `LealoneAgentAdapter`
- 构造时接收可测试的 Lealone 执行依赖或配置，避免单元测试必须启动完整命令行终端
- `start()` 建立或校验底层 Lealone 会话资源，并把状态从 `NEW` 切到 `ACTIVE`
- `submit(String input)` 沿用 `InteractiveSession` 的非空和活动态校验，把输入交给 Lealone 执行入口，并把返回文本按顺序追加到输出缓冲
- 执行失败时进入 `ERROR`，后续输入必须被拒绝；已有输出仍可通过 `drainOutput()` 读取
- `close()` 释放底层资源并进入 `CLOSED`，重复关闭保持幂等

如果 Lealone 提供的命令行类只适合终端进程式运行，应在本项目侧抽出最小 adapter 内部执行接口，让生产实现调用 Lealone JDBC/client 能力，测试实现用 fake executor 验证可观察会话语义。

## Key Decisions

- **adapter 只做 Lealone 会话封装**：避免把 M29 后续的 loop bridge、command parser、SHOW/audit 能力提前塞进同一个 change。
- **继续以 `InteractiveSession` 为唯一公共边界**：后续 bridge 依赖本项目契约，而不是直接依赖 `com.lealone.client.LealoneClient` 或 `com.lealone.agent.LealoneAgent`。
- **输出沿用 drain 语义**：adapter 可以把每次 Lealone 执行结果追加到缓冲，不要求 Lealone 交互本身必须是同步单响应模型。
- **失败转入 `ERROR` 而不是静默吞掉异常**：调用方可以通过 `state()` 观察 terminal failure，且后续 `submit()` 会按既有契约拒绝。
- **不处理 Question/Answer 回写**：本 change 只提供交互表面；将输入映射成 `Answer` 并恢复 loop 属于后续 `interactive-command-parser` 和 `loop-question-interactive-bridge`。

## Alternatives Considered

- **直接运行 `LealoneAgent.main()` 并把标准输入/输出重定向到 session**：不作为首选。它更像终端进程入口，测试和资源关闭边界较重；只有在没有更窄 Lealone client API 时才作为内部实现细节考虑。
- **在 adapter 中直接接入 `DefaultLoopDriver` 暂停恢复**：被否决。这样会把 bridge change 合并进 adapter，扩大状态机风险。
- **在 adapter 中直接解析自然语言为 `Answer`**：被否决。命令解析和 Question/Answer 绑定规则需要独立 spec 和测试，属于 `interactive-command-parser`。
- **新增一个不同于 `InteractiveSession` 的 Lealone 专用接口**：被否决。`interactive-session-interface` 已经定义了稳定边界，新增并行接口会让后续 M29 工作出现两套会话语义。
