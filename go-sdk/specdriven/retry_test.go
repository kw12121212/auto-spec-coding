package specdriven

import (
	"context"
	"errors"
	"io"
	"net/http"
	"strings"
	"testing"
	"time"
)

func TestRetries429Response(t *testing.T) {
	attempts := 0
	client, err := NewClient("http://example.com",
		WithRetry(1, 0),
		WithHTTPClient(&http.Client{Transport: roundTripFunc(func(req *http.Request) (*http.Response, error) {
			attempts++
			if attempts == 1 {
				return jsonResponse(http.StatusTooManyRequests, `{"status":429,"error":"rate_limited","message":"too many"}`), nil
			}
			return jsonResponse(http.StatusOK, `{"status":"ok","version":"0.1.0"}`), nil
		})}),
	)
	if err != nil {
		t.Fatalf("NewClient returned error: %v", err)
	}

	health, err := client.Health(context.Background())
	if err != nil {
		t.Fatalf("Health returned error: %v", err)
	}
	if attempts != 2 {
		t.Fatalf("expected two attempts, got %d", attempts)
	}
	if health.Status != "ok" {
		t.Fatalf("unexpected health response: %+v", health)
	}
}

func TestRetries5xxResponse(t *testing.T) {
	attempts := 0
	client, err := NewClient("http://example.com",
		WithRetry(1, 0),
		WithHTTPClient(&http.Client{Transport: roundTripFunc(func(req *http.Request) (*http.Response, error) {
			attempts++
			if attempts == 1 {
				return jsonResponse(http.StatusInternalServerError, `{"status":500,"error":"internal","message":"boom"}`), nil
			}
			return jsonResponse(http.StatusOK, `{"tools":[]}`), nil
		})}),
	)
	if err != nil {
		t.Fatalf("NewClient returned error: %v", err)
	}

	tools, err := client.ListTools(context.Background())
	if err != nil {
		t.Fatalf("ListTools returned error: %v", err)
	}
	if attempts != 2 {
		t.Fatalf("expected two attempts, got %d", attempts)
	}
	if len(tools.Tools) != 0 {
		t.Fatalf("expected empty tools, got %d", len(tools.Tools))
	}
}

func TestDoesNotRetryValidationError(t *testing.T) {
	attempts := 0
	client, err := NewClient("http://example.com",
		WithRetry(3, 0),
		WithHTTPClient(&http.Client{Transport: roundTripFunc(func(req *http.Request) (*http.Response, error) {
			attempts++
			return jsonResponse(http.StatusBadRequest, `{"status":400,"error":"invalid_params","message":"bad prompt"}`), nil
		})}),
	)
	if err != nil {
		t.Fatalf("NewClient returned error: %v", err)
	}

	_, err = client.RunAgent(context.Background(), RunAgentRequest{Prompt: "hello"})
	apiErr := requireAPIError(t, err)
	if attempts != 1 {
		t.Fatalf("expected one attempt, got %d", attempts)
	}
	if apiErr.Retryable() {
		t.Fatal("expected 400 to be non-retryable")
	}
}

func TestRetryBudgetIsEnforced(t *testing.T) {
	attempts := 0
	client, err := NewClient("http://example.com",
		WithRetry(2, 0),
		WithHTTPClient(&http.Client{Transport: roundTripFunc(func(req *http.Request) (*http.Response, error) {
			attempts++
			return jsonResponse(http.StatusBadGateway, `{"status":502,"error":"llm_error","message":"upstream"}`), nil
		})}),
	)
	if err != nil {
		t.Fatalf("NewClient returned error: %v", err)
	}

	_, err = client.Health(context.Background())
	apiErr := requireAPIError(t, err)
	if attempts != 3 {
		t.Fatalf("expected three attempts, got %d", attempts)
	}
	if apiErr.StatusCode != http.StatusBadGateway || !apiErr.Retryable() {
		t.Fatalf("unexpected error: %+v", apiErr)
	}
}

func TestRetryWaitHonorsContext(t *testing.T) {
	ctx, cancel := context.WithCancel(context.Background())
	cancel()
	err := waitForRetry(ctx, time.Second)
	if !errors.Is(err, context.Canceled) {
		t.Fatalf("expected context canceled, got %v", err)
	}
}

type roundTripFunc func(*http.Request) (*http.Response, error)

func (f roundTripFunc) RoundTrip(req *http.Request) (*http.Response, error) {
	return f(req)
}

func jsonResponse(status int, body string) *http.Response {
	return &http.Response{
		StatusCode: status,
		Header:     http.Header{"Content-Type": []string{"application/json"}},
		Body:       io.NopCloser(strings.NewReader(body)),
	}
}

func textResponse(status int, body string) *http.Response {
	return &http.Response{
		StatusCode: status,
		Header:     http.Header{"Content-Type": []string{"text/plain"}},
		Body:       io.NopCloser(strings.NewReader(body)),
	}
}
