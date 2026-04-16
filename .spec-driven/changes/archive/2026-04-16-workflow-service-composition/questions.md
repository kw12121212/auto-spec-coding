# Questions: workflow-service-composition

<!-- No open questions -->

## Resolved

- [x] Q: 步骤执行模型是数据驱动（声明时内联步骤描述符）还是代码驱动（运行时注册）？
  Context: 影响 delta spec 结构与步骤执行器接口形态。
  A: 数据驱动——步骤在声明时内联描述，与 SQL CREATE WORKFLOW 路径保持一致。（用户确认：接受所有建议）
