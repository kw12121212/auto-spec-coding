# Questions: http-e2e-tests

## Open

<!-- No open questions -->

## Resolved

- [x] Q: Is Tomcat (`org.apache.catalina:startup.Tomcat`) available at test compile scope via the `lealone-http` transitive dependency, or does it need to be added explicitly to `pom.xml` test scope?
  Context: The e2e test approach depends on embedded Tomcat. If the class is not available, we may need to add a test-scoped Tomcat dependency or use an alternative embedded container.
  A: 实现时先运行 `mvn dependency:tree -Dscope=test | grep catalina` 检查。如果不可用，在 `pom.xml` 添加 `org.apache.tomcat.embed:tomcat-embed-core` 的 test scope 依赖。

- [x] Q: Should `StubTool` be extracted to a shared test utility class (e.g., `src/test/java/org/specdriven/agent/testutil/StubTool.java`) so it can be reused by both `HttpApiServletTest` and `HttpE2eTest`, or should `HttpE2eTest` define its own local copy?
  Context: `StubTool` is currently defined as a private inner class inside `HttpApiServletTest`. The e2e test needs the same stub. Extracting it reduces duplication but changes existing test files.
  A: 在 `HttpE2eTest` 中定义本地副本。不修改已有测试文件，保持 change scope 纯测试添加。
