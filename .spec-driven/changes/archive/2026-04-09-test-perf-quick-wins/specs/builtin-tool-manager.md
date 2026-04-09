# Builtin Tool Manager Spec (Delta: test-perf-quick-wins)

## MODIFIED Requirements

### Requirement: Classpath resource extraction

- When `resolve(BuiltinTool.RG)` needs to extract from the classpath, the application MUST package the platform-specific resource path returned by `BuiltinTool.RG.resourcePath(platform)` for the current supported build platform

#### Scenario: Current platform bundled `rg` resource is packaged
- GIVEN the application resources for the current supported build platform
- WHEN `BuiltinTool.RG.resourcePath(Platform.detect())` is computed
- THEN the classpath MUST contain a readable resource at that exact path
