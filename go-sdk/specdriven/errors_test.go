package specdriven

import (
	"context"
	"errors"
	"net/http"
	"testing"
)

func TestAPIErrorResponseIsPreserved(t *testing.T) {
	client, err := NewClient("http://example.com", WithHTTPClient(&http.Client{
		Transport: roundTripFunc(func(req *http.Request) (*http.Response, error) {
			return jsonResponse(http.StatusUnprocessableEntity, `{"status":422,"error":"tool_error","message":"bad tool"}`), nil
		}),
	}))
	if err != nil {
		t.Fatalf("NewClient returned error: %v", err)
	}

	_, err = client.ListTools(context.Background())
	apiErr := requireAPIError(t, err)
	if apiErr.StatusCode != http.StatusUnprocessableEntity {
		t.Fatalf("unexpected status: %d", apiErr.StatusCode)
	}
	if apiErr.Code != "tool_error" || apiErr.Message != "bad tool" {
		t.Fatalf("unexpected api error: %+v", apiErr)
	}
	if apiErr.Retryable() {
		t.Fatal("expected 422 to be non-retryable")
	}
}

func TestNonJSONErrorResponsePreservesStatus(t *testing.T) {
	client, err := NewClient("http://example.com", WithHTTPClient(&http.Client{
		Transport: roundTripFunc(func(req *http.Request) (*http.Response, error) {
			return textResponse(http.StatusNotFound, "missing"), nil
		}),
	}))
	if err != nil {
		t.Fatalf("NewClient returned error: %v", err)
	}

	_, err = client.GetAgentState(context.Background(), "missing")
	apiErr := requireAPIError(t, err)
	if apiErr.StatusCode != http.StatusNotFound {
		t.Fatalf("unexpected status: %d", apiErr.StatusCode)
	}
	if apiErr.Message != "missing" {
		t.Fatalf("unexpected message: %q", apiErr.Message)
	}
	if apiErr.Retryable() {
		t.Fatal("expected 404 to be non-retryable")
	}
}

func TestNetworkErrorIsRetryable(t *testing.T) {
	networkErr := errors.New("dial failed")
	client, err := NewClient("http://example.com", WithHTTPClient(&http.Client{
		Transport: roundTripFunc(func(req *http.Request) (*http.Response, error) {
			return nil, networkErr
		}),
	}))
	if err != nil {
		t.Fatalf("NewClient returned error: %v", err)
	}

	_, err = client.Health(context.Background())
	apiErr := requireAPIError(t, err)
	if apiErr.Code != "network_error" {
		t.Fatalf("unexpected code: %q", apiErr.Code)
	}
	if !apiErr.Retryable() {
		t.Fatal("expected network error to be retryable")
	}
	if !errors.Is(apiErr, networkErr) {
		t.Fatalf("expected APIError to wrap network error")
	}
}

func requireAPIError(t *testing.T, err error) *APIError {
	t.Helper()
	if err == nil {
		t.Fatal("expected error")
	}
	var apiErr *APIError
	if !errors.As(err, &apiErr) {
		t.Fatalf("expected APIError, got %T: %v", err, err)
	}
	return apiErr
}
