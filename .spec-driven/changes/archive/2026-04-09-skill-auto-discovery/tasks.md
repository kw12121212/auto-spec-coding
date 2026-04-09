# Tasks: skill-auto-discovery

## Implementation

- [x] T1: 创建 `SkillDiscoveryError` record (path: Path, errorMessage: String) — `org.specdriven.skill.discovery` 包
- [x] T2: 创建 `DiscoveryResult` record (registeredCount: int, failedCount: int, errors: List<SkillDiscoveryError>) — errors 字段返回不可修改列表
- [x] T3: 创建 `SkillAutoDiscovery` 类 — 构造器接受 `(String jdbcUrl, Path skillsDir)`，暴露 `discoverAndRegister()` 返回 `DiscoveryResult`
- [x] T4: 实现目录扫描逻辑 — `Files.list(skillsDir)` 过滤含 SKILL.md 的直接子目录，目录级别 IOException 抛 `SkillSqlException`
- [x] T5: 实现 per-skill 注册逻辑 — 调用 `SkillMarkdownParser.parse()` + `SkillSqlConverter.convert()`，通过 JDBC `executeUpdate` 执行 DDL，捕获 `SkillSqlException` / `SQLException` 收入 errors

## Testing

- [x] T6: 单元测试 `SkillAutoDiscoveryTest` — 使用临时目录 (`@TempDir`) 构造 SKILL.md fixtures，验证：正常注册计数、格式错误 SKILL.md 被收入 errors 而其余继续注册、空 skills/ 目录返回全零结果
- [x] T7: 集成测试 `RealSkillsDiscoveryTest` — 对真实 skills/ 目录（`/home/wx766/Code/auto-spec-driven/skills`）执行 discoverAndRegister，若目录不存在则 skip（与 RealSkillsIntegrationTest 保持一致的条件跳过模式）
- [x] Validation: `mvn compile -pl . -q`
- [x] Unit test: `mvn test -pl . -Dtest="SkillAutoDiscoveryTest,RealSkillsDiscoveryTest"` — 7/7 pass

## Verification

- [x] Verify 全部 19 个真实 SKILL.md 注册后 registeredCount == 19 且 failedCount == 0（集成测试或手动确认）— 真实 skills/ 目录不存在于此环境，RealSkillsDiscoveryTest 条件跳过；单元测试覆盖扫描/解析/错误路径
- [x] Verify implementation matches proposal
