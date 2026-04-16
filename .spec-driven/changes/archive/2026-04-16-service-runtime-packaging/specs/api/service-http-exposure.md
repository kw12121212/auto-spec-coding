---
mapping:
  implementation:
    - src/main/java/org/specdriven/agent/http/HttpApiServlet.java
    - src/main/java/org/specdriven/agent/http/ServiceHttpInvocationHandler.java
    - src/main/java/org/specdriven/agent/http/ServiceRuntimeLauncher.java
    - src/main/java/org/specdriven/agent/http/ServiceInvoker.java
    - src/main/java/org/specdriven/agent/http/LealoneServiceInvoker.java
  tests:
    - src/test/java/org/specdriven/agent/http/HttpApiServletTest.java
    - src/test/java/org/specdriven/agent/http/ServiceRuntimeLauncherTest.java
---

## ADDED Requirements

### Requirement: Runtime-packaged service HTTP availability

A service application started through the supported Java runtime entrypoint MUST expose application-service HTTP invocation through the existing service HTTP namespace.

#### Scenario: packaged runtime exposes service namespace
- GIVEN the service runtime entrypoint has successfully started a supported service application
- WHEN an authenticated caller sends `POST /services/{serviceName}/{methodName}` for a supported service method
- THEN the request MUST use the existing application-service HTTP invocation contract
- AND the response behavior MUST match the existing service HTTP exposure requirements

#### Scenario: packaged runtime preserves agent API namespace
- GIVEN the service runtime entrypoint has successfully started a supported service application
- WHEN a caller uses an existing `/api/v1/*` agent API route
- THEN the route MUST keep its existing observable behavior
- AND the service runtime MUST NOT reinterpret `/api/v1/*` requests as application-service calls

#### Scenario: service HTTP startup failure is reported
- GIVEN service application bootstrap succeeds
- AND the application-service HTTP endpoint cannot be started with the supplied runtime settings
- WHEN the operator starts the service application through the supported Java runtime entrypoint
- THEN startup MUST fail explicitly
- AND the failure output MUST identify the HTTP startup failure
- AND startup MUST NOT report the service application as available
