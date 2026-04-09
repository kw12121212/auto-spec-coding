# Questions: skill-sql-schema

## Open

## Resolved

- [x] Q: instructions_path 的存储路径约定是什么？
  Context: 影响 PARAMETERS 中路径值和 skill-instructions-store 的设计。
  A: 不需要 instructions_path。指令体直接通过 PARAMETERS 'instructions' 内联存储在 DB 内，无需外部文件引用。

- [x] Q: 服务名中连字符在 Lealone 中是否需要反引号包裹？
  Context: Lealone quoteIdentifier 的行为。
  A: converter 直接用反引号包裹所有服务名，与 Lealone quoteIdentifier 行为一致。
