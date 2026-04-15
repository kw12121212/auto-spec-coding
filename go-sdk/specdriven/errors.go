package specdriven

import (
	"fmt"
	"net/http"
)

// APIError is returned for HTTP API failures and network failures.
type APIError struct {
	StatusCode int
	Code       string
	Message    string
	retryable  bool
	cause      error
}

// Error returns a compact caller-facing error string.
func (e *APIError) Error() string {
	if e == nil {
		return ""
	}
	if e.StatusCode > 0 && e.Code != "" && e.Message != "" {
		return fmt.Sprintf("specdriven: http %d %s: %s", e.StatusCode, e.Code, e.Message)
	}
	if e.StatusCode > 0 && e.Code != "" {
		return fmt.Sprintf("specdriven: http %d %s", e.StatusCode, e.Code)
	}
	if e.StatusCode > 0 && e.Message != "" {
		return fmt.Sprintf("specdriven: http %d: %s", e.StatusCode, e.Message)
	}
	if e.StatusCode > 0 {
		return fmt.Sprintf("specdriven: http %d", e.StatusCode)
	}
	if e.Message != "" {
		return "specdriven: " + e.Message
	}
	return "specdriven: request failed"
}

// Unwrap returns the underlying network error, when present.
func (e *APIError) Unwrap() error {
	if e == nil {
		return nil
	}
	return e.cause
}

// Retryable reports whether retrying the same request is safe.
func (e *APIError) Retryable() bool {
	return e != nil && e.retryable
}

func newHTTPError(statusCode int, code, message string) *APIError {
	if message == "" {
		message = http.StatusText(statusCode)
	}
	return &APIError{
		StatusCode: statusCode,
		Code:       code,
		Message:    message,
		retryable:  isRetryableStatus(statusCode),
	}
}

func newNetworkError(err error) *APIError {
	message := "network error"
	if err != nil {
		message = err.Error()
	}
	return &APIError{
		Code:      "network_error",
		Message:   message,
		retryable: true,
		cause:     err,
	}
}
