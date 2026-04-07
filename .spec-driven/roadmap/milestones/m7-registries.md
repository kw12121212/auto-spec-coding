# M7 - 任务与团队注册表

## Goal

实现任务注册表和团队注册表，使用 Lealone 嵌入式数据库提供持久化存储的任务追踪和团队协作基础设施。

## In Scope

- 任务注册表（创建、查询、更新、删除、状态流转）
- 团队注册表（成员管理、角色分配、团队创建/解散）
- Lealone 嵌入式数据库存储实现（替代纯内存方案，提供持久化）

## Out of Scope

- Cron 定时任务注册表（M8）
- 权限策略（M6）
- 分布式部署场景（单进程嵌入模式）

## Done Criteria

- 任务注册表 CRUD 操作均可正常工作
- 任务状态流转（pending -> in_progress -> completed/deleted）正确
- 团队注册表的成员加入/移除和角色分配正确
- 数据通过 Lealone DB 持久化，重启后可恢复
- 有单元测试覆盖边界条件（空 ID、重复创建等）

## Planned Changes

- `registry-tasks` - Declared: complete - 任务注册表实现（Lealone DB 持久化）
- `registry-teams` - Declared: planned - 团队注册表实现（Lealone DB 持久化）

## Dependencies

- M1 核心接口（基础类型定义）
- Lealone 数据库模块（lealone-db, lealone-sql）

## Risks

- Lealone 嵌入式模式的初始化和关闭生命周期管理
- 任务状态机的合法转换路径需要明确定义

## Status

- Declared: proposed

## Notes

- 使用 Lealone 嵌入式数据库替代 Go 版本的纯内存注册表，提供开箱即用的持久化
- 与 M6（权限）无强依赖，可并行开发
- Lealone 的 SQL 引擎可用于实现复杂查询（按状态过滤、按团队过滤等）
