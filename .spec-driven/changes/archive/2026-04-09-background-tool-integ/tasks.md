# Tasks: background-tool-integ

## Implementation

- [x] 修改 AgentContext 接口，添加 processManager() 方法
- [x] 修改 SimpleAgentContext，添加 ProcessManager 支持和构造函数重载
- [x] 修改 DefaultAgent.stop()，调用 ProcessManager.stopAll() 清理后台进程
- [x] 修改 DefaultAgent.close()，添加后台进程清理保险机制
- [x] 在 DefaultAgent 中添加 cleanupBackgroundProcesses() 辅助方法

## Testing

- [x] 运行 `mvn compile` 确保代码编译通过（lint/validation）
- [x] 运行 `mvn test` 确保所有单元测试通过（unit test）
- [x] 添加 BackgroundToolIntegrationTest 测试类（unit test）
  - [x] 测试 Agent 停止时活跃后台进程被终止
  - [x] 测试 Agent 关闭时无活跃进程的场景
  - [x] 测试 ProcessManager 不存在时的正常行为
  - [x] 测试多个后台进程同时被清理的场景

## Verification

- [x] 验证 DefaultAgent.stop() 调用 ProcessManager.stopAll()
- [x] 验证 AgentContext.processManager() 返回正确的 Optional
- [x] 验证 SimpleAgentContext 正确传递 ProcessManager
- [x] 验证所有新测试通过
