# M29 - SQL+自然语言交互式人机协作

## Goal

将 Lealone Agent 的交互式 SQL+自然语言对话能力接入本项目 LoopDriver 自主循环架构，作为"人在回路"通道：当自主循环遇到无法决策的问题时，用户可通过自然语言或 SQL 指导 agent 行为，实现自主执行与人工介入的无缝切换。

## In Scope

- InteractiveSession 接口：定义交互式会话的生命周期（启动、输入、输出、关闭）
- LealoneAgentAdapter：将 Lealone 的 LealoneClient 交互能力包装为本项目的 InteractiveSession 实现
- LoopDriver ↔ InteractiveSession 集成点：循环暂停时转入交互模式，用户指令完成后恢复循环
- 自然语言指令解析：将用户的 NL 输入转化为 agent 可执行的 action（跳过 change、调整优先级、手动指定方向等）
- SHOW SERVICES / SHOW STATUS 命令：在交互中查看当前系统状态
- 交互日志审计：每次人工介入的操作记录到 AuditLogStore

## Out of Scope

- Lealone Agent 本身的 NL→SQL 翻译能力（由 Lealone 上游负责）
- Web 终端 UI（首期基于命令行交互）
- 多用户同时交互的并发控制
- 交互内容的语音输入/输出

## Done Criteria

- LoopDriver 在遇到无法自主决策的场景时 MUST 能暂停并进入交互式模式
- 用户在交互模式中输入的自然语言/SQL 指令 MUST 能被解析并影响循环行为
- `SHOW SERVICES` / `SHOW STATUS` 命令 MUST 在交互模式中可用，返回当前系统状态快照
- 交互结束后循环 MUST 能从暂停点正确恢复执行
- 每次人工介入操作 MUST 记录到 AuditLogStore，包含操作者、时间、指令内容和影响范围
- 有单元测试覆盖交互启停、指令解析、循环恢复、审计记录场景

## Planned Changes

- `interactive-session-interface` - Declared: planned - 定义 InteractiveSession 接口与标准生命周期协议，含输入/输出/状态查询方法
- `lealone-agent-adapter` - Declared: planned - 实现 LealoneAgentAdapter：包装 Lealone LealoneClient 为 InteractiveSession，桥接其 SQL+NL 交互能力
- `loop-interactive-gate` - Declared: planned - 扩展 DefaultLoopDriver：增加交互门控状态机，支持从自主循环平滑切换到交互模式再恢复
- `nl-command-parser` - Declared: planned - 实现自然语言指令解析器：将用户输入映射为循环控制动作（skip/change-priority/set-direction/resume）
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

## Status
- Declared: proposed

## Notes

- 交互门控应与 M26 的升级门控协同设计：M26 处理"必须人工确认"的本项目内部升级，M29 处理"用户主动介入"的外部指导
- 首期交互模式仅在 LoopDriver 层面开放，不暴露给 HTTP/JSON-RPC 远程调用方
- NL 解析首期支持有限的指令集（约 10 条核心命令），逐步扩展
- LealoneAgent 交互式窗口可作为独立入口运行（类似 lealone -agent 模式），也可嵌入 LoopDriver
