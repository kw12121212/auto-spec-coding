# M30 - 动态编译与 Skill 热加载

## Goal

复用 Lealone 重构后的 SourceCompiler 编译缓存持久化机制（`d376e29`, `5b4a001`, `32930d8`），为本项目的 Skill 系统提供 Java 源码动态编译、class 文件缓存和热加载的基础链路，支持在受信工作流中运行时注册与替换自定义 skill，并为后续治理能力奠定基础。

## In Scope

- SkillSourceCompiler 接口：封装 Lealone SourceCompiler，提供 skill 源码编译入口
- ClassCacheManager：管理编译产物的 class 文件持久化与按需加载
- SkillHotLoader：运行时注册/注销/替换 skill 实现类的完整生命周期
- 编译产物隔离：每个 skill 使用独立的 ClassLoader，避免类冲突
- 编译失败回退：skill 编译失败时不影响已加载的其他 skill
- 与现有 SkillAutoDiscovery 和 SkillExecutorPlugin 的集成

## Out of Scope

- 非 Java 语言 skill（Python/JS 等）的动态编译
- 分布式 skill 编译与分发
- skill 代码的 IDE 级调试支持
- skill 版本管理与灰度发布
- 面向不受信租户或公网开放的代码上传与执行入口
- 对动态编译代码提供安全沙箱保证
- 默认关闭、权限门控、审计记录与受信来源治理（由后续 milestone 覆盖）

## Done Criteria

- 提供 Java 源码字符串 → 可执行 Skill 实例的完整链路（编译 → 缓存 → 加载 → 注册）
- 编译后的 class 文件 MUST 持久化到磁盘，下次启动直接加载无需重新编译
- 已运行的 skill 可在运行时被热替换为新版本，不影响其他 skill
- 编译失败的 skill MUST 被隔离，不得影响系统中其他 skill 的正常运行
- 每个 skill MUST 使用独立的 ClassLoader，同名类在不同 skill 间不冲突
- 编译失败的新版本 MUST 保持旧版本继续可用，不得自动替换健康版本
- 有单元测试覆盖编译成功/失败、缓存命中/失效、热加载/卸载、ClassLoader 隔离场景

## Planned Changes
- `skill-source-compiler` - Declared: complete - 封装 Lealone SourceCompiler 为 SkillSourceCompiler 接口，提供 skill 源码编译、错误报告与 class 产出能力
- `class-cache-manager` - Declared: complete - 实现 ClassCacheManager：基于 Service.getClassDir() 模式管理编译产物的磁盘持久化、版本检测与按需加载
- `skill-hot-loader` - Declared: complete - 实现 SkillHotLoader：运行时 skill 的注册、注销、热替换全生命周期管理，含 ClassLoader 隔离
- `compile-fallback-isolation` - Declared: complete - 编译失败隔离机制：捕获编译异常、隔离失败 skill、维护健康 skill 的可用性
- `hot-load-integration` - Declared: complete - 将动态编译与热加载能力集成到 SkillAutoDiscovery 和 BuiltinToolManager，统一 skill 发现与加载流程

## Dependencies

- M09-M11 Skill 系列（SkillAutoDiscovery、SkillExecutorPlugin、SkillInstructionsStore）
- M02 Tool Surface 基础工具集（Tool 接口与 BuiltinToolManager）
- Lealone SourceCompiler（重构后的 class 持久化版本）
- Lealone 更新：`d376e29` 编译完自动保存 class 文件、`5b4a001` 写 class 时序修复、`32930d8` getClassDir() 统一

## Risks

- 动态加载的 class 可能存在内存泄漏（ClassLoader 无法回收）
- 热替换过程中的请求路由可能导致短暂的不一致状态
- 编译产物的磁盘安全：恶意 skill 源码可能利用编译过程执行任意代码
- JDK 版本兼容性：SourceCompiler 使用 javax.tools.JavaCompiler，在某些受限环境可能不可用
- 若在治理能力落地前直接暴露该能力，动态编译入口仍会带来明显安全风险

## Status
- Declared: complete

## Notes

- 首期仅支持"源码字符串入参"模式，不支持从文件系统扫描源码目录（避免安全问题）
- ClassLoader 隔离策略：每个 skill 一个 URLClassLoader，关闭时释放所有加载的类
- 编译缓存 key = skillName + sourceHash，源码不变时直接用缓存
- 参考 Lealone Service.getExecutor() 的延迟创建 + synchronized 双重检查模式
- 默认关闭、权限、审计和来源治理由后续 milestone 覆盖；在共享或生产环境开放该能力前应与治理 milestone 配套规划






