package org.specdriven.agent.http;

final class ServiceInvocationException extends RuntimeException {

    private final int status;
    private final String error;

    private ServiceInvocationException(int status, String error, String message, Throwable cause) {
        super(message, cause);
        this.status = status;
        this.error = error;
    }

    static ServiceInvocationException notFound(String message, Throwable cause) {
        return new ServiceInvocationException(404, "not_found", message, cause);
    }

    static ServiceInvocationException failed(String message, Throwable cause) {
        return new ServiceInvocationException(500, "service_error", message, cause);
    }

    int status() {
        return status;
    }

    String error() {
        return error;
    }
}
