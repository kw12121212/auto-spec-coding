package specdriven

import (
	"context"
	"errors"
	"net/http"
	"net/http/httptest"
	"reflect"
	"testing"
	"time"
)

func TestNewEventsRejectsNilClient(t *testing.T) {
	events, err := NewEvents(nil)
	if err == nil {
		t.Fatal("expected nil client error")
	}
	if events != nil {
		t.Fatalf("expected nil events, got %+v", events)
	}
}

func TestPollEventsSendsQueryAuthAndDecodesEvents(t *testing.T) {
	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		if r.Method != http.MethodGet {
			t.Fatalf("unexpected method: %s", r.Method)
		}
		if r.URL.Path != "/api/v1/events" {
			t.Fatalf("unexpected path: %s", r.URL.Path)
		}
		if got := r.Header.Get("X-API-Key"); got != "api-key-1" {
			t.Fatalf("unexpected api key: %q", got)
		}
		if got := r.URL.Query().Get("after"); got != "7" {
			t.Fatalf("unexpected after cursor: %q", got)
		}
		if got := r.URL.Query().Get("limit"); got != "2" {
			t.Fatalf("unexpected limit: %q", got)
		}
		if got := r.URL.Query().Get("type"); got != "ERROR" {
			t.Fatalf("unexpected type: %q", got)
		}
		writeJSON(w, http.StatusOK, `{"events":[{"sequence":8,"type":"ERROR","timestamp":1000,"source":"agent-1","metadata":{"message":"failed","retryable":false}}],"nextCursor":8}`)
	}))
	defer server.Close()

	client, err := NewClient(server.URL, WithAPIKey("api-key-1"))
	if err != nil {
		t.Fatalf("NewClient returned error: %v", err)
	}
	response, err := client.PollEvents(context.Background(), EventPollOptions{
		After: 7,
		Limit: 2,
		Type:  "ERROR",
	})
	if err != nil {
		t.Fatalf("PollEvents returned error: %v", err)
	}
	if response.NextCursor != 8 || len(response.Events) != 1 {
		t.Fatalf("unexpected poll response: %+v", response)
	}
	event := response.Events[0]
	if event.Sequence != 8 || event.Type != "ERROR" || event.Timestamp != 1000 || event.Source != "agent-1" {
		t.Fatalf("unexpected event: %+v", event)
	}
	if event.Metadata["message"] != "failed" || event.Metadata["retryable"] != false {
		t.Fatalf("unexpected metadata: %+v", event.Metadata)
	}
}

func TestPollEventsEmptyListDecodesAsEmptySlice(t *testing.T) {
	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		writeJSON(w, http.StatusOK, `{"events":[],"nextCursor":5}`)
	}))
	defer server.Close()

	events := newTestEvents(t, server.URL)
	response, err := events.Poll(context.Background(), EventPollOptions{})
	if err != nil {
		t.Fatalf("Poll returned error: %v", err)
	}
	if response.Events == nil {
		t.Fatal("expected empty non-nil events slice")
	}
	if len(response.Events) != 0 || response.NextCursor != 5 {
		t.Fatalf("unexpected response: %+v", response)
	}
}

func TestPollEventsPreservesAPIErrors(t *testing.T) {
	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		writeJSON(w, http.StatusBadRequest, `{"status":400,"error":"invalid_params","message":"Invalid query param 'type'"}`)
	}))
	defer server.Close()

	events := newTestEvents(t, server.URL)
	_, err := events.Poll(context.Background(), EventPollOptions{Type: "NOPE"})
	if err == nil {
		t.Fatal("expected API error")
	}
	var apiErr *APIError
	if !errors.As(err, &apiErr) {
		t.Fatalf("expected APIError, got %T", err)
	}
	if apiErr.StatusCode != http.StatusBadRequest || apiErr.Code != "invalid_params" {
		t.Fatalf("unexpected API error: %+v", apiErr)
	}
}

func TestPollEventsRespectsCanceledContext(t *testing.T) {
	events := newTestEvents(t, "http://example.com")
	ctx, cancel := context.WithCancel(context.Background())
	cancel()

	_, err := events.Poll(ctx, EventPollOptions{})

	if !errors.Is(err, context.Canceled) {
		t.Fatalf("expected canceled context, got %v", err)
	}
}

func TestSubscribeDeliversInOrderAdvancesCursorAndSkipsDuplicates(t *testing.T) {
	ctx, cancel := context.WithCancel(context.Background())
	defer cancel()
	requests := 0
	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		requests++
		switch requests {
		case 1:
			if got := r.URL.Query().Get("after"); got != "" {
				t.Fatalf("unexpected first after cursor: %q", got)
			}
			writeJSON(w, http.StatusOK, `{"events":[{"sequence":1,"type":"AGENT_STATE_CHANGED","timestamp":1000,"source":"agent-1","metadata":{}},{"sequence":2,"type":"ERROR","timestamp":1001,"source":"agent-1","metadata":{}}],"nextCursor":2}`)
		case 2:
			if got := r.URL.Query().Get("after"); got != "2" {
				t.Fatalf("unexpected second after cursor: %q", got)
			}
			writeJSON(w, http.StatusOK, `{"events":[{"sequence":2,"type":"ERROR","timestamp":1001,"source":"agent-1","metadata":{}},{"sequence":3,"type":"AGENT_STATE_CHANGED","timestamp":1002,"source":"agent-1","metadata":{}}],"nextCursor":3}`)
		default:
			t.Fatalf("unexpected extra poll request %d with after=%s", requests, r.URL.Query().Get("after"))
		}
	}))
	defer server.Close()

	events := newTestEvents(t, server.URL)
	var delivered []int64
	err := events.Subscribe(ctx, EventSubscribeOptions{PollInterval: time.Millisecond}, func(ctx context.Context, event Event) error {
		delivered = append(delivered, event.Sequence)
		if event.Sequence == 3 {
			cancel()
		}
		return nil
	})

	if !errors.Is(err, context.Canceled) {
		t.Fatalf("expected canceled context, got %v", err)
	}
	if !reflect.DeepEqual(delivered, []int64{1, 2, 3}) {
		t.Fatalf("unexpected delivered events: %+v", delivered)
	}
	if requests != 2 {
		t.Fatalf("expected two poll requests, got %d", requests)
	}
}

func TestSubscribeReturnsPollingErrors(t *testing.T) {
	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		writeJSON(w, http.StatusInternalServerError, `{"status":500,"error":"internal","message":"boom"}`)
	}))
	defer server.Close()

	events := newTestEvents(t, server.URL)
	err := events.Subscribe(context.Background(), EventSubscribeOptions{PollInterval: time.Millisecond}, func(context.Context, Event) error {
		return nil
	})

	var apiErr *APIError
	if !errors.As(err, &apiErr) {
		t.Fatalf("expected APIError, got %T", err)
	}
	if apiErr.StatusCode != http.StatusInternalServerError {
		t.Fatalf("unexpected API error: %+v", apiErr)
	}
}

func TestSubscribeRejectsNilHandler(t *testing.T) {
	events := newTestEvents(t, "http://example.com")

	err := events.Subscribe(context.Background(), EventSubscribeOptions{}, nil)

	if err == nil {
		t.Fatal("expected nil handler error")
	}
}

func TestPollEventsCanSendBearerTypeAndLimitOnly(t *testing.T) {
	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		if got := r.Header.Get("Authorization"); got != "Bearer token-1" {
			t.Fatalf("unexpected bearer token: %q", got)
		}
		if got := r.URL.Query().Get("limit"); got != "10" {
			t.Fatalf("unexpected limit: %q", got)
		}
		if got := r.URL.Query().Get("type"); got != "AGENT_STATE_CHANGED" {
			t.Fatalf("unexpected type: %q", got)
		}
		if got := r.URL.Query().Get("after"); got != "" {
			t.Fatalf("after should be omitted when zero: %q", got)
		}
		writeJSON(w, http.StatusOK, `{"events":[],"nextCursor":0}`)
	}))
	defer server.Close()

	client, err := NewClient(server.URL, WithBearerToken("token-1"))
	if err != nil {
		t.Fatalf("NewClient returned error: %v", err)
	}
	if _, err := client.PollEvents(context.Background(), EventPollOptions{Limit: 10, Type: "AGENT_STATE_CHANGED"}); err != nil {
		t.Fatalf("PollEvents returned error: %v", err)
	}
}

func newTestEvents(t *testing.T, baseURL string) *Events {
	t.Helper()
	client, err := NewClient(baseURL)
	if err != nil {
		t.Fatalf("NewClient returned error: %v", err)
	}
	events, err := NewEvents(client)
	if err != nil {
		t.Fatalf("NewEvents returned error: %v", err)
	}
	return events
}
