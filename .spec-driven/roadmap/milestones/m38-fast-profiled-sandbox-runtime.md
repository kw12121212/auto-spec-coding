# M38 - 快速 Profile 化沙箱运行环境

## Goal

基于 Sandlock 为项目发起的命令、工具和开发技术栈提供轻量 profile 隔离，让 JDK、Node.js、Go、Python 等运行环境、缓存目录、工具链路径和进程权限可以按项目或任务隔离，避免多项目之间的版本、缓存、环境变量和端口/网络策略冲突，同时保持命令启动足够快，不把本项目变成容器平台。

## In Scope

- 项目级环境 profile 声明、加载、选择和错误报告契约
- 首期技术栈 profile 覆盖 JDK、Node.js、Go、Python 的可观察环境隔离需求
- 基于 Sandlock 的命令运行入口，支持通过命名 profile 启动受隔离进程
- 每个 profile 独立的 PATH、HOME、缓存目录和语言工具链环境
- BashTool、后台进程和自主 loop phase 命令执行的 profile 绑定规则
- Sandlock 不可用、目标系统不满足要求或 profile 无效时的快速失败诊断
- profile 使用过程中的权限边界、审计事件和健康状态观测

## Out of Scope

- 替代 Docker、VM、CI runner 或通用容器编排平台
- 非 Linux 平台上的等价内核级隔离实现
- 自动下载和安装所有 JDK、Node.js、Go、Python 版本
- 多租户强隔离、安全沙箱证明或远程代码执行平台
- 生产环境远程部署、修复或服务编排能力
- 对已有工具权限模型的语义重写

## Done Criteria

- 系统 MUST 支持从受支持配置中加载命名环境 profile，并能列出当前项目可用 profile
- profile MUST 能声明 JDK、Node.js、Go、Python 至少一种技术栈的可观察运行环境，包括可执行路径、环境变量和缓存目录
- 使用 profile 执行命令时，系统 MUST 通过 Sandlock 或等价的受支持 Sandlock 入口启动进程，而不是直接复用宿主全局环境
- 同一宿主机上不同 profile 的 HOME、缓存目录和语言工具链路径 MUST 可配置为相互独立，避免默认共享 Maven/npm/go/pip 等状态
- BashTool、后台进程和 loop phase 命令 MUST 能在调用方明确指定的 profile 下运行；未指定时 MUST 使用项目默认 profile 或明确回退规则
- 当 Sandlock 不存在、Linux/kernel 能力不满足要求、profile 引用缺失或技术栈路径无效时，系统 MUST 快速失败并返回可诊断原因
- profile 运行 MUST 继续经过现有权限检查；任何新增 profile 权限或 Sandlock 策略不得绕过既有 PermissionProvider 语义
- 系统 MUST 发布或记录 profile 选择、启动失败和隔离运行结果的最小审计信息
- 自动化测试 MUST 覆盖 profile 解析、默认 profile 选择、显式 profile 执行、工具链隔离、Sandlock 不可用快速失败、无效 profile 失败、权限拒绝和后台进程 profile 绑定场景

## Planned Changes

- `environment-profile-contract` - Declared: planned - 定义项目级环境 profile 的声明、解析、选择优先级和可观察错误契约，首期覆盖 JDK、Node.js、Go、Python 技术栈
- `sandlock-runner-integration` - Declared: planned - 集成 Sandlock 可用性检测与命令封装入口，支持通过命名 profile 启动受隔离进程并返回结构化执行结果
- `profile-toolchain-isolation` - Declared: planned - 为每个 profile 提供独立的 PATH、HOME、缓存目录和语言工具链环境，避免 Maven/npm/go/pip 等状态跨项目污染
- `profile-tool-execution-binding` - Declared: planned - 将 BashTool、后台进程和 loop phase 命令执行接入 profile 选择规则，保证项目命令在预期隔离环境中运行
- `profile-governance-observability` - Declared: planned - 增加 profile 使用的权限边界、审计事件、健康诊断和不支持环境的快速失败输出

## Dependencies

- M02 Tool Surface 基础工具集，提供 BashTool 和基础命令执行面
- M06 权限模型与执行钩子，保证 profile 化命令执行继续受权限检查约束
- M15 后台进程管理，承接 server 类进程在 profile 下的生命周期管理
- M32 Lealone 平台化统一基础设施，提供统一配置、健康检查和指标观测底座
- Sandlock 可用性与目标 Linux/kernel 能力满足受支持运行要求

## Risks

- Sandlock 依赖 Linux Landlock/seccomp 等内核能力，开发机或 CI 环境不满足要求时需要清晰降级或快速失败
- profile 边界若定义过宽，容易扩张为通用语言版本管理器或容器平台
- 工具链缓存隔离不完整会继续造成跨项目污染，过度隔离则可能显著降低构建速度
- BashTool、后台进程和 loop phase 若采用不同 profile 选择规则，会导致同一任务内环境不一致
- 若 Sandlock 策略与现有权限模型职责不清，可能出现双重拒绝、误放行或难以诊断的执行失败

## Status

- Declared: proposed

## Notes

- Sandlock 当前提供轻量 Linux 进程沙箱、saved profile、clean environment、COW 文件系统、资源限制和网络限制等能力，适合作为本里程碑的隔离执行底座
- 本里程碑聚焦“项目技术栈环境隔离”，不是安全边界完整替代方案；安全治理仍以现有权限模型和后续明确 spec 为准
- 首期 profile 应优先覆盖本项目真实使用的 JDK/Maven、Node.js、Go、Python 工具链和缓存目录，避免为未使用语言预先扩张配置模型
- 如果后续需要自动安装语言运行时或远程执行环境，应另开独立 milestone，而不是扩大本里程碑
