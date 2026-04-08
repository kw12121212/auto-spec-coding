package org.specdriven.agent.http;

/**
 * HTTP-layer exception carrying status code, error code, and message.
 */
public class HttpApiException extends RuntimeException {

    private final int httpStatus;
    private final String errorCode;

    public HttpApiException(int httpStatus, String errorCode, String message) {
        super(message);
        this.httpStatus = httpStatus;
        this.errorCode = errorCode;
    }

    public int httpStatus() {
        return httpStatus;
    }

    public String errorCode() {
        return errorCode;
    }

    public ErrorResponse toErrorResponse() {
        return new ErrorResponse(httpStatus, errorCode, getMessage(), null);
    }
}
