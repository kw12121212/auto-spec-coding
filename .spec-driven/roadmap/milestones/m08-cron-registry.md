# M08 - 定时任务注册表

## Goal

实现 cron 定时任务注册表，利用 Lealone 调度引擎支持周期性任务调度和一次性延时任务。

## In Scope

- Cron 表达式解析与调度器
- 定时任务注册表（创建、查询、删除）
- 一次性延时任务（fire-once）
- 任务执行的错误处理与重试
- Lealone 数据库持久化任务定义

## Out of Scope

- 分布式调度（单进程内）
- 任务错过触发的恢复

## Done Criteria

- Cron 任务可按表达式周期性触发
- 一次性任务可在指定时间后触发
- 任务可被注册、查询、取消
- 任务定义通过 Lealone DB 持久化
- 有单元测试覆盖调度精度和错误场景

## Planned Changes

- `registry-cron` - Declared: complete - Cron 调度器与定时任务注册表实现（基于 Lealone 调度能力）

## Dependencies

- M01 核心接口（基础类型定义）
- M07 注册表（参考注册表实现模式）
- Lealone 调度引擎

## Risks

- Cron 调度精度受 JVM 定时器精度限制
- 长时间运行进程中的线程资源管理

## Status

- Declared: complete

## Notes

- 使用 Lealone 的调度引擎替代 Go 标准库 time.Timer，提供更可靠的调度能力
- 参考 spec-coding-sdk 的 cron 调度行为（最长 7 天自动过期等）
- 任务定义持久化到 Lealone DB，支持重启后恢复
