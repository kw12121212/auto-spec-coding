# Tasks: orchestrator-question-pause

## Implementation

- [x] 扩展 orchestrator 与 agent 生命周期实现：当运行中提出需要等待外部答复的问题时，创建结构化 `Question`、切换到 `PAUSED`、并阻止后续 LLM / Tool 执行
- [x] 增加 waiting question 的运行时管理：同一 session 只允许一个未决问题，支持匹配答复恢复与超时过期
- [x] 补齐问题等待阶段的事件、审计与会话历史写入，确保创建、答复、过期和恢复行为与规格一致

## Testing

- [x] 运行 `mvn -q -DskipTests compile` 作为 lint / validation，确认新增运行时与测试代码可编译
- [x] 运行 `mvn -q test -Dsurefire.useFile=false` 作为 unit test 回归
- [x] 增加单元测试覆盖：问题触发暂停、等待期间不继续调用 LLM / Tool、匹配答复恢复同一会话、超时过期、过期后拒绝晚到答复、暂停状态下 stop/close 清理

## Verification

- [x] 验证实现范围仅限运行时 pause/wait/resume，不新增 SDK / HTTP / JSON-RPC 公开接口
- [x] 验证无 question 的普通 orchestrator 运行路径保持现有行为不变
- [x] 验证本 change 只实现单 session 单 waiting question 语义，不偷偷扩展到多问题并发
