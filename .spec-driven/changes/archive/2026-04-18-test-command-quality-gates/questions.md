# Questions: test-command-quality-gates

## Open

<!-- No open questions -->

## Resolved

- [x] Q: checkstyle 违规是否应在首次引入时阻塞构建？
  Context: 当前 pom.xml 未配置 checkstyle 插件，现有代码是否符合 Google Java Style 未知。设为 `failsOnError=true` 可能立即阻塞构建流水线，影响日常开发。设为 `failsOnError=false` 可先建立入口而不阻塞，后续收紧。此决定影响 pom.xml 中 checkstyle 插件的初始配置。
  A: 初始设为 failsOnError=false、violationSeverity=warning，先建立入口不阻塞开发，后续变更再收紧。
