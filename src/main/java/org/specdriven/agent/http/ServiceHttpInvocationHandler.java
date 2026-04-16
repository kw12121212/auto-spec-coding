package org.specdriven.agent.http;

final class ServiceHttpInvocationHandler {

    private ServiceHttpInvocationHandler() {
    }

    static String invoke(ServiceInvoker serviceInvoker, String serviceName, String methodName, String body) {
        if (serviceInvoker == null) {
            throw new HttpApiException(404, "not_found", "Service HTTP exposure is not configured");
        }
        if (body == null || body.isBlank()) {
            throw new HttpApiException(400, "invalid_params", "Request body required");
        }
        ServiceInvocationRequest request = HttpJsonCodec.decodeServiceInvocationRequest(body);
        try {
            Object result = serviceInvoker.invoke(serviceName, methodName, request.args());
            return HttpJsonCodec.encode(new ServiceInvocationResponse(result));
        } catch (ServiceInvocationException e) {
            throw new HttpApiException(e.status(), e.error(), e.getMessage());
        } catch (IllegalArgumentException e) {
            throw new HttpApiException(400, "invalid_params", e.getMessage());
        } catch (RuntimeException e) {
            throw new HttpApiException(500, "service_error",
                    e.getMessage() != null ? e.getMessage() : "Service invocation failed");
        }
    }
}
