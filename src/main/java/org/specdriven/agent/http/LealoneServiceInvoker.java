package org.specdriven.agent.http;

import com.lealone.db.service.Service;
import com.lealone.db.session.ServerSession;
import com.lealone.service.ServiceHandler;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

final class LealoneServiceInvoker implements ServiceInvoker {

    private final ServiceHandler serviceHandler;

    LealoneServiceInvoker(String jdbcUrl) {
        Objects.requireNonNull(jdbcUrl, "jdbcUrl must not be null");
        this.serviceHandler = new ServiceHandler(Map.of("jdbc_url", jdbcUrl));
    }

    @Override
    public Object invoke(String serviceName, String methodName, List<Object> args) {
        Objects.requireNonNull(serviceName, "serviceName must not be null");
        Objects.requireNonNull(methodName, "methodName must not be null");
        List<Object> safeArgs = args != null ? args : List.of();
        try {
            return Service.execute(
                    session(),
                    fullyQualifiedServiceMethod(serviceName, methodName),
                    HttpJsonCodec.encodeJsonArray(safeArgs));
        } catch (RuntimeException e) {
            if (isMissingServiceOrMethod(e)) {
                throw ServiceInvocationException.notFound(
                        "Service or method not found: " + serviceName + "." + methodName, e);
            }
            throw ServiceInvocationException.failed(
                    "Service invocation failed: " + serviceName + "." + methodName, e);
        }
    }

    private ServerSession session() {
        return serviceHandler.getSession();
    }

    private String fullyQualifiedServiceMethod(String serviceName, String methodName) {
        String[] parts = serviceName.split("\\.");
        if (parts.length == 1) {
            return session().getDatabase().getName() + "." + currentSchemaName()
                    + "." + serviceName + "." + methodName;
        }
        if (parts.length == 2) {
            return session().getDatabase().getName() + "." + serviceName + "." + methodName;
        }
        if (parts.length == 3) {
            return serviceName + "." + methodName;
        }
        throw ServiceInvocationException.notFound(
                "Service or method not found: " + serviceName + "." + methodName, null);
    }

    private String currentSchemaName() {
        String schema = session().getCurrentSchemaName();
        return schema == null || schema.isBlank() ? "public" : schema;
    }

    private boolean isMissingServiceOrMethod(RuntimeException e) {
        String message = e.getMessage();
        if (message == null && e.getCause() != null) {
            message = e.getCause().getMessage();
        }
        if (message == null) {
            return false;
        }
        String normalized = message.toLowerCase(Locale.ROOT);
        return normalized.contains("not found")
                || normalized.contains("no method")
                || normalized.contains("method not found");
    }
}
