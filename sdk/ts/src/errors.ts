/** Thrown for HTTP API failures and network errors. */
export class ApiError extends Error {
  readonly status: number;
  readonly code: string;
  readonly retryable: boolean;
  readonly cause: unknown;

  constructor(params: {
    status: number;
    code: string;
    message: string;
    retryable: boolean;
    cause?: unknown;
  }) {
    super(formatMessage(params));
    this.name = "ApiError";
    this.status = params.status;
    this.code = params.code;
    this.retryable = params.retryable;
    this.cause = params.cause;
    // Restore prototype chain for instanceof checks in transpiled environments
    Object.setPrototypeOf(this, new.target.prototype);
  }

  static fromHttpStatus(
    status: number,
    code: string,
    message: string,
  ): ApiError {
    return new ApiError({
      status,
      code,
      message: message || httpStatusText(status),
      retryable: isRetryableStatus(status),
    });
  }

  static fromNetworkError(cause: unknown): ApiError {
    let message: string;
    if (cause instanceof Error) {
      message = cause.message;
    } else if (cause != null) {
      message = String(cause);
    } else {
      message = "network error";
    }
    return new ApiError({
      status: 0,
      code: "network_error",
      message,
      retryable: true,
      cause,
    });
  }
}

function formatMessage(params: {
  status: number;
  code: string;
  message: string;
}): string {
  const { status, code, message } = params;
  if (status > 0 && code && message) {
    return `specdriven: http ${status} ${code}: ${message}`;
  }
  if (status > 0 && code) {
    return `specdriven: http ${status} ${code}`;
  }
  if (status > 0 && message) {
    return `specdriven: http ${status}: ${message}`;
  }
  if (status > 0) {
    return `specdriven: http ${status}`;
  }
  if (message) {
    return `specdriven: ${message}`;
  }
  return "specdriven: request failed";
}

export function isRetryableStatus(status: number): boolean {
  return status === 429 || status >= 500;
}

const STATUS_TEXT: Record<number, string> = {
  400: "Bad Request",
  401: "Unauthorized",
  403: "Forbidden",
  404: "Not Found",
  405: "Method Not Allowed",
  409: "Conflict",
  422: "Unprocessable Entity",
  429: "Too Many Requests",
  500: "Internal Server Error",
  502: "Bad Gateway",
  503: "Service Unavailable",
  504: "Gateway Timeout",
};

function httpStatusText(status: number): string {
  return STATUS_TEXT[status] ?? `HTTP ${status}`;
}
