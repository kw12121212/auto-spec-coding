---
mapping:
  implementation:
    - src/main/java/org/specdriven/agent/http/HttpApiServlet.java
    - src/main/java/org/specdriven/agent/http/HttpJsonCodec.java
    - src/main/java/org/specdriven/agent/http/LealoneServiceInvoker.java
    - src/main/java/org/specdriven/agent/http/ServiceHttpInvocationHandler.java
    - src/main/java/org/specdriven/agent/http/ServiceInvocationException.java
    - src/main/java/org/specdriven/agent/http/ServiceInvocationRequest.java
    - src/main/java/org/specdriven/agent/http/ServiceInvocationResponse.java
    - src/main/java/org/specdriven/agent/http/ServiceInvoker.java
    - src/main/java/org/specdriven/agent/http/ServiceRuntimeLauncher.java
  tests:
    - src/test/java/org/specdriven/agent/http/AuthFilterTest.java
    - src/test/java/org/specdriven/agent/http/HttpApiServletTest.java
    - src/test/java/org/specdriven/agent/http/HttpModelsTest.java
    - src/test/java/org/specdriven/agent/http/ServiceRuntimeLauncherTest.java
---

# Service HTTP Exposure

## Requirements

### Requirement: Application service HTTP namespace

The system MUST expose supported Lealone Service method invocations through an application-service HTTP namespace that is separate from the existing `/api/v1/*` agent management API.

#### Scenario: Service method route is accepted
- GIVEN a supported Lealone Service named `invoice`
- AND the service exposes a supported method named `create`
- WHEN an authenticated caller sends `POST /services/invoice/create`
- THEN the request MUST be handled as an application-service method invocation

#### Scenario: Agent API namespace remains separate
- GIVEN the application-service HTTP namespace is available
- WHEN a caller uses existing `/api/v1/*` agent API routes
- THEN those routes MUST keep their existing observable behavior
- AND the service HTTP namespace MUST NOT reinterpret `/api/v1/*` requests as service method calls

### Requirement: Positional JSON invocation body

Service method invocation requests MUST accept a JSON object containing an `args` array of positional arguments.

#### Scenario: Positional arguments are accepted
- GIVEN a supported service method that accepts positional arguments
- WHEN an authenticated caller sends `POST /services/{serviceName}/{methodName}` with body `{"args":[1,"x"]}`
- THEN the invocation MUST receive the arguments in array order

#### Scenario: Missing args is rejected
- GIVEN a caller sends `POST /services/{serviceName}/{methodName}` with a JSON body that does not contain `args`
- WHEN the request is processed
- THEN an error response with status `400` and error `invalid_params` MUST be returned

#### Scenario: Non-array args is rejected
- GIVEN a caller sends `POST /services/{serviceName}/{methodName}` with a JSON body whose `args` field is not an array
- WHEN the request is processed
- THEN an error response with status `400` and error `invalid_params` MUST be returned

### Requirement: Service invocation response contract

Service method invocation MUST return a stable JSON response for successful calls and stable HTTP error responses for observable invocation failures.

#### Scenario: Successful service invocation returns result
- GIVEN a supported service method returns a value
- WHEN an authenticated caller invokes the method through `POST /services/{serviceName}/{methodName}`
- THEN the response MUST return HTTP 200
- AND the response body MUST include the service method result under `result`

#### Scenario: Unknown service or method returns not found
- GIVEN no supported service or method matches the requested path
- WHEN an authenticated caller sends `POST /services/{serviceName}/{methodName}`
- THEN an error response with status `404` and error `not_found` MUST be returned

#### Scenario: Unsupported HTTP method is rejected
- GIVEN a caller sends a non-POST request to `/services/{serviceName}/{methodName}`
- WHEN the request is processed
- THEN an error response with status `405` and error `method_not_allowed` MUST be returned

#### Scenario: Service execution failure is surfaced
- GIVEN a supported service method fails during execution
- WHEN an authenticated caller invokes that method through the service HTTP namespace
- THEN an error response with status `500` and error `service_error` MUST be returned
- AND the response MUST NOT report the failed service invocation as a successful result

### Requirement: Service HTTP exposure authentication boundary

Application-service HTTP invocation MUST use the existing HTTP authentication and filter boundary by default.

#### Scenario: Unauthenticated service invocation is rejected
- GIVEN the HTTP authentication boundary is enabled
- WHEN an unauthenticated caller sends `POST /services/{serviceName}/{methodName}`
- THEN the request MUST be rejected by the HTTP authentication boundary
- AND the service method MUST NOT be invoked

#### Scenario: Authenticated service invocation reaches route handling
- GIVEN the HTTP authentication boundary is enabled
- WHEN an authenticated caller sends `POST /services/{serviceName}/{methodName}`
- THEN the request MAY reach service method route handling

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
