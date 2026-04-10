# Questions: answer-agent-runtime

## Open

<!-- No open questions -->

## Resolved

### Question: Answer Agent 的 LLM Provider 如何选择？

**Decision**: 通过 `LlmProviderRegistry` 动态获取

**Rationale**: 与现有架构（M5 LLM Provider Layer）保持一致，配置中只需存储 provider 名称和模型名称，无需存储完整的客户端实例。支持 Answer Agent 使用与主 Agent 不同的 provider（如主 Agent 用 Claude，Answer Agent 用轻量级 OpenAI 模型）。

**Implementation**: 
```java
public record AnswerAgentConfig(
    String providerName,  // "openai", "claude"
    String model,         // "gpt-4o-mini"
    ...
) {}
```

---

### Question: ContextWindowManager 的裁剪算法是否需要可配置？

**Decision**: 首期保持简单（固定规则），后续如有需求再扩展

**Rationale**: 消息数裁剪已能满足大部分场景；token 计数需要引入额外依赖或复杂计算；可插拔策略会增加不必要的抽象复杂度。符合 YAGNI 原则。

**Implementation**: 固定规则：保留最近 `maxContextMessages` 条（默认 10）+ 所有 system 消息

**Future Extension**:
```java
// 未来可能的扩展
public interface ContextCropStrategy {
    List<Message> crop(List<Message> messages, Question question);
}
```

---

### Question: Answer Agent 的答复内容格式如何规范？

**Decision**: 允许自由文本，系统填充其他字段

**Rationale**: 更简单可靠，无需解析 JSON；`basisSummary` 可由系统生成（如 "Based on conversation context"）；`confidence` 使用固定值 0.9；减少 LLM 输出格式的约束，降低失败概率。

**Implementation**:
- `content` = LLM 返回的自由文本
- `basisSummary` = "Based on conversation context"
- `confidence` = 0.9
- `source` = `AI_AGENT`
- `decision` = `ANSWER_ACCEPTED`

---

### Question: 测试命令如何运行？

**Decision**: 使用标准 Maven 命令

**Verified Commands**:
```bash
# 编译
mvn compile

# 运行所有测试
mvn test

# 运行特定测试类
mvn test -Dtest=AnswerAgentRuntimeTest

# 编译和测试
mvn clean test
```

**Note**: `pom.xml` 中没有配置 checkstyle 插件，代码风格检查使用 IDE 功能或后续添加。

