# M29 - SQL+自然语言交互式人机协作

## Goal

将 Lealone Agent 的交互式 SQL+自然语言对话能力接入本项目 LoopDriver 自主循环架构，作为现有 Question/Answer 升级链路上的“人在回路”交互入口：当自主循环因必须人工处理的问题而暂停时，用户可通过自然语言或 SQL 查看状态、提交受支持的指令并完成答复，实现自主执行与人工介入的无缝切换。

## In Scope

- InteractiveSession 接口：定义交互式会话的生命周期（启动、输入、输出、关闭）
- LealoneAgentAdapter：将 Lealone 的 LealoneClient 交互能力包装为本项目的 InteractiveSession 实现
- LoopDriver ↔ InteractiveSession 集成点：循环因人工升级问题暂停时转入交互模式，用户指令完成后通过既有恢复路径恢复循环
- 自然语言指令解析：将用户的 NL 输入转化为受支持的 Question Answer 或有限循环控制动作
- SHOW SERVICES / SHOW STATUS 命令：在交互中查看当前系统状态
- 交互日志审计：每次人工介入的操作记录到 AuditLogStore
- 与现有 Question/Answer 生命周期集成：交互输入和结果回写到等待中的 question，而不是绕过现有模型

## Out of Scope

- Lealone Agent 本身的 NL→SQL 翻译能力（由 Lealone 上游负责）
- Web 终端 UI（首期基于命令行交互）
- 多用户同时交互的并发控制
- 交互内容的语音输入/输出
- 绕过 Question/Answer 生命周期直接修改 LoopDriver 内部状态
- 任意开放式的循环控制台或新的并行控制平面

## Done Criteria

- LoopDriver 在遇到必须人工处理的 Question 并暂停后 MUST 能进入交互式模式
- 用户在交互模式中输入的自然语言/SQL 指令 MUST 能被解析为与等待中的 Question 绑定的受支持动作，并影响循环后续行为
- `SHOW SERVICES` / `SHOW STATUS` 命令 MUST 在交互模式中可用，返回当前系统状态快照
- 交互过程中产生的答复、指令和审计上下文 MUST 通过现有 Question/Answer 生命周期持久化并可审计
- 交互结束后循环 MUST 通过现有暂停/恢复路径从暂停点正确恢复执行
- 每次人工介入操作 MUST 记录到 AuditLogStore，包含操作者、时间、指令内容和影响范围
- 有单元测试覆盖交互启停、指令解析、循环恢复、审计记录场景

## Planned Changes
- `interactive-session-interface` - Declared: complete - 定义 InteractiveSession 接口与标准生命周期协议，含输入/输出/状态查询方法
- `lealone-agent-adapter` - Declared: complete - 实现 LealoneAgentAdapter：包装 Lealone LealoneClient 为 InteractiveSession，桥接其 SQL+NL 交互能力
- `loop-question-interactive-bridge` - Declared: complete - 扩展 DefaultLoopDriver：在现有人工升级 question 暂停点接入交互模式，并通过既有恢复路径恢复循环
- `interactive-command-parser` - Declared: planned - 实现受限指令解析器：将用户输入映射为与等待 question 绑定的答复或有限循环控制动作
- `interactive-show-audit` - Declared: planned - 实现 SHOW 命令（SERVICES/STATUS/ROADMAP）与交互审计日志记录，复用已有 Store 查询数据并扩展 AuditEntry

## Dependencies

- M24 内置自主循环执行流水线（LoopDriver 状态机基础）
- M22 交互问题解析与多通道回复（Question/Answer 模型复用）
- M26 自主循环恢复与升级控制（升级门控与本 milestone 的交互门控互补）
- Lealone 更新：`f8aa436` SQL+NL 交互对话、`107ca14` LealoneAgent 动态执行命令、`4c552c2` SHOW SERVICES

## Risks

- 交互模式的引入会增加 LoopDriver 状态机复杂度，状态转换路径显著增多
- 自然语言指令解析准确率有限，误解析可能导致意外的循环行为变更
- 长时间交互等待可能导致后台资源（数据库连接、HTTP 会话等）超时释放
- 交互权限控制不当可能允许普通用户绕过 permission hook 直接操控循环
- 若交互入口绕过现有 Question/Answer 生命周期，将造成审计、恢复和权限语义分裂

## Status
- Declared: proposed

## Notes

- 交互门控应与 M26 的升级门控协同设计：M26 决定何时需要人工处理，M29 提供该人工处理的交互入口
- 首期交互模式仅在 LoopDriver 层面开放，不暴露给 HTTP/JSON-RPC 远程调用方
- NL 解析首期支持有限的指令集（约 10 条核心命令），并优先复用现有 QuestionCategory / DeliveryMode / Answer 语义
- LealoneAgent 交互式窗口可作为独立入口运行（类似 lealone -agent 模式），也可嵌入 LoopDriver

