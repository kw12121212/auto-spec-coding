# M16 - 集成与发布

## Goal

完成端到端集成验证、文档编写和发布准备。

## In Scope

- 端到端集成验证（SDK + JSON-RPC + HTTP 三层接口一致性）
- README 和 API 文档
- Maven 中央仓库发布准备
- 示例代码和快速入门指南

## Out of Scope

- CI/CD pipeline 搭建（可作为后续增强）
- 各接口层的独立测试（已在 M12-M13 各自完成）

## Done Criteria

- 可执行一次完整的 demo 流程（创建 agent -> 注册工具 -> 运行循环 -> 获取结果）
- README 和 API 文档完整
- Maven artifact 可被第三方正确引入
- 三层接口（SDK / JSON-RPC / HTTP）行为一致

## Planned Changes
- `integration-testing` - Declared: complete - 端到端集成测试与接口层一致性验证
- `release-prep` - Declared: planned - 文档完善、Maven 发布配置、示例代码

## Dependencies

- M11 Native Java SDK 层
- M12 JSON-RPC 接口
- M13 HTTP REST API

## Risks

- 三层接口的行为一致性验证工作量大
- Maven 中央仓库发布流程可能需要额外配置

## Status

- Declared: active

## Notes

- 此里程碑是整个项目的收尾阶段
- 发布为 Maven artifact，支持通过 pom.xml / build.gradle 引入

