# Questions: environment-profile-contract

## Open

<!-- No open questions -->

## Resolved

- [x] Q: 环境 profile 的主 spec 应该放在哪个目录？
  Context: 这决定 delta spec 路径和后续主 spec 组织方式。
  A: 放在 `.spec-driven/specs/config/` 下。

- [x] Q: 首期支持的 profile 来源是什么？
  Context: 这决定 proposal 的范围、配置示例与校验场景。
  A: v1 仅支持项目 YAML 配置中的 profile 声明。

- [x] Q: 默认 profile 选择规则是什么？
  Context: 这决定 profile 解析契约，以及后续 bash/background/loop 接入时的兼容边界。
  A: 选择优先级为显式指定 profile 优先，否则使用项目默认 profile；不存在“无 profile”状态，至少要落到默认 profile。
