package specdriven

import (
	"context"
	"encoding/json"
	"errors"
	"net/http"
	"net/http/httptest"
	"os"
	"testing"
	"time"
)

// ====== Hermetic integration tests (run by default via go test ./...) ======

// TestIntegrationClientAndAllFacadesShareAuth verifies that a single client
// with bearer auth correctly applies (or omits) authentication across the
// Client, Agent, Tools, and Events public facades against one backend server.
func TestIntegrationClientAndAllFacadesShareAuth(t *testing.T) {
	receivedAuth := map[string]string{}
	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		receivedAuth[r.URL.Path] = r.Header.Get("Authorization")
		switch r.URL.Path {
		case "/api/v1/health":
			writeJSON(w, http.StatusOK, `{"status":"ok","version":"1.0.0"}`)
		case "/api/v1/tools":
			writeJSON(w, http.StatusOK, `{"tools":[]}`)
		case "/api/v1/agent/run":
			writeJSON(w, http.StatusOK, `{"agentId":"a1","output":"done","state":"STOPPED"}`)
		case "/api/v1/events":
			writeJSON(w, http.StatusOK, `{"events":[],"nextCursor":0}`)
		default:
			w.WriteHeader(http.StatusNotFound)
		}
	}))
	defer server.Close()

	client, err := NewClient(server.URL, WithBearerToken("integration-token"))
	if err != nil {
		t.Fatalf("NewClient returned error: %v", err)
	}
	agent, err := NewAgent(client)
	if err != nil {
		t.Fatalf("NewAgent returned error: %v", err)
	}
	tools, err := NewTools(client)
	if err != nil {
		t.Fatalf("NewTools returned error: %v", err)
	}
	events, err := NewEvents(client)
	if err != nil {
		t.Fatalf("NewEvents returned error: %v", err)
	}

	ctx := context.Background()
	if _, err := client.Health(ctx); err != nil {
		t.Fatalf("Health returned error: %v", err)
	}
	if _, err := tools.List(ctx); err != nil {
		t.Fatalf("tools.List returned error: %v", err)
	}
	if _, err := agent.Run(ctx, "hello"); err != nil {
		t.Fatalf("agent.Run returned error: %v", err)
	}
	if _, err := events.Poll(ctx, EventPollOptions{}); err != nil {
		t.Fatalf("events.Poll returned error: %v", err)
	}

	// Health is unauthenticated; all other SDK calls must carry the bearer token.
	if got := receivedAuth["/api/v1/health"]; got != "" {
		t.Errorf("health: expected no auth header, got %q", got)
	}
	for _, path := range []string{"/api/v1/tools", "/api/v1/agent/run", "/api/v1/events"} {
		if got := receivedAuth[path]; got != "Bearer integration-token" {
			t.Errorf("path %s: expected bearer auth, got %q", path, got)
		}
	}
}

// TestIntegrationAgentRunStopStateWorkflow verifies the complete agent
// lifecycle through the Agent facade against a single backend server.
func TestIntegrationAgentRunStopStateWorkflow(t *testing.T) {
	const agentID = "workflow-agent-1"
	var requestLog []string

	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		requestLog = append(requestLog, r.Method+" "+r.URL.Path)
		switch r.URL.Path {
		case "/api/v1/agent/run":
			if r.Method != http.MethodPost {
				t.Errorf("agent/run: unexpected method %s", r.Method)
			}
			var body RunAgentRequest
			if err := json.NewDecoder(r.Body).Decode(&body); err != nil {
				w.WriteHeader(http.StatusBadRequest)
				return
			}
			if body.Prompt != "list files" {
				t.Errorf("agent/run: unexpected prompt %q", body.Prompt)
			}
			writeJSON(w, http.StatusOK, `{"agentId":"`+agentID+`","output":null,"state":"RUNNING"}`)
		case "/api/v1/agent/state":
			if r.Method != http.MethodGet {
				t.Errorf("agent/state: unexpected method %s", r.Method)
			}
			if got := r.URL.Query().Get("id"); got != agentID {
				t.Errorf("agent/state: unexpected id %q", got)
			}
			writeJSON(w, http.StatusOK, `{"agentId":"`+agentID+`","state":"STOPPED","createdAt":1000,"updatedAt":2000}`)
		case "/api/v1/agent/stop":
			if r.Method != http.MethodPost {
				t.Errorf("agent/stop: unexpected method %s", r.Method)
			}
			if got := r.URL.Query().Get("id"); got != agentID {
				t.Errorf("agent/stop: unexpected id %q", got)
			}
			w.WriteHeader(http.StatusOK)
		default:
			w.WriteHeader(http.StatusNotFound)
		}
	}))
	defer server.Close()

	client, err := NewClient(server.URL)
	if err != nil {
		t.Fatalf("NewClient returned error: %v", err)
	}
	agent, err := NewAgent(client)
	if err != nil {
		t.Fatalf("NewAgent returned error: %v", err)
	}
	ctx := context.Background()

	runResp, err := agent.Run(ctx, "list files")
	if err != nil {
		t.Fatalf("Run returned error: %v", err)
	}
	if runResp.AgentID != agentID || runResp.State != "RUNNING" || runResp.Output != nil {
		t.Fatalf("unexpected run response: %+v", runResp)
	}

	state, err := agent.State(ctx, runResp.AgentID)
	if err != nil {
		t.Fatalf("State returned error: %v", err)
	}
	if state.AgentID != agentID || state.State != "STOPPED" || state.CreatedAt != 1000 || state.UpdatedAt != 2000 {
		t.Fatalf("unexpected state response: %+v", state)
	}

	if err := agent.Stop(ctx, runResp.AgentID); err != nil {
		t.Fatalf("Stop returned error: %v", err)
	}

	if len(requestLog) != 3 {
		t.Fatalf("expected 3 backend requests, got %d: %v", len(requestLog), requestLog)
	}
}

// TestIntegrationToolsListAndRegisterWorkflow verifies combined tool listing
// and remote tool registration through the Tools facade against one server.
func TestIntegrationToolsListAndRegisterWorkflow(t *testing.T) {
	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		switch r.URL.Path {
		case "/api/v1/tools":
			if r.Method != http.MethodGet {
				t.Errorf("tools list: unexpected method %s", r.Method)
			}
			writeJSON(w, http.StatusOK, `{"tools":[{"name":"bash","description":"run commands","parameters":[{"name":"command","type":"string","required":true}]}]}`)
		case "/api/v1/tools/register":
			if r.Method != http.MethodPost {
				t.Errorf("tools register: unexpected method %s", r.Method)
			}
			if got := r.Header.Get("Authorization"); got != "Bearer reg-token" {
				t.Errorf("tools register: unexpected auth %q", got)
			}
			var req RemoteToolRegistrationRequest
			if err := json.NewDecoder(r.Body).Decode(&req); err != nil {
				w.WriteHeader(http.StatusBadRequest)
				return
			}
			if req.Name != "lookup" || req.CallbackURL != "http://localhost:9999/callback" {
				t.Errorf("tools register: unexpected body %+v", req)
			}
			resp := ToolInfo{Name: req.Name, Description: req.Description, Parameters: []map[string]any{}}
			w.Header().Set("Content-Type", "application/json")
			_ = json.NewEncoder(w).Encode(resp)
		default:
			w.WriteHeader(http.StatusNotFound)
		}
	}))
	defer server.Close()

	client, err := NewClient(server.URL, WithBearerToken("reg-token"))
	if err != nil {
		t.Fatalf("NewClient returned error: %v", err)
	}
	tools, err := NewTools(client)
	if err != nil {
		t.Fatalf("NewTools returned error: %v", err)
	}
	ctx := context.Background()

	list, err := tools.List(ctx)
	if err != nil {
		t.Fatalf("List returned error: %v", err)
	}
	if len(list.Tools) != 1 || list.Tools[0].Name != "bash" {
		t.Fatalf("unexpected tools list: %+v", list)
	}

	registered, err := tools.RegisterRemote(ctx, RemoteToolRegistrationRequest{
		Name:        "lookup",
		Description: "lookup data",
		CallbackURL: "http://localhost:9999/callback",
		Parameters:  []map[string]any{{"name": "term", "type": "string"}},
	})
	if err != nil {
		t.Fatalf("RegisterRemote returned error: %v", err)
	}
	if registered.Name != "lookup" || registered.Description != "lookup data" {
		t.Fatalf("unexpected registered tool: %+v", registered)
	}
}

// TestIntegrationEventPollingWithCursorAndTypeFilter verifies that event poll
// options are correctly serialized as query parameters and the response is
// decoded through the Events facade.
func TestIntegrationEventPollingWithCursorAndTypeFilter(t *testing.T) {
	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		if r.URL.Path != "/api/v1/events" {
			w.WriteHeader(http.StatusNotFound)
			return
		}
		after := r.URL.Query().Get("after")
		typ := r.URL.Query().Get("type")
		limit := r.URL.Query().Get("limit")
		if after != "5" {
			t.Errorf("events poll: expected after=5, got %q", after)
		}
		if typ != "AGENT_STATE_CHANGED" {
			t.Errorf("events poll: expected type=AGENT_STATE_CHANGED, got %q", typ)
		}
		if limit != "10" {
			t.Errorf("events poll: expected limit=10, got %q", limit)
		}
		writeJSON(w, http.StatusOK, `{"events":[{"sequence":6,"type":"AGENT_STATE_CHANGED","timestamp":2000,"source":"agent-1","metadata":{"agentId":"agent-1"}}],"nextCursor":6}`)
	}))
	defer server.Close()

	client, err := NewClient(server.URL)
	if err != nil {
		t.Fatalf("NewClient returned error: %v", err)
	}
	events, err := NewEvents(client)
	if err != nil {
		t.Fatalf("NewEvents returned error: %v", err)
	}

	response, err := events.Poll(context.Background(), EventPollOptions{After: 5, Limit: 10, Type: "AGENT_STATE_CHANGED"})
	if err != nil {
		t.Fatalf("Poll returned error: %v", err)
	}
	if len(response.Events) != 1 {
		t.Fatalf("expected one event, got %d", len(response.Events))
	}
	ev := response.Events[0]
	if ev.Sequence != 6 || ev.Type != "AGENT_STATE_CHANGED" || ev.Source != "agent-1" || ev.Timestamp != 2000 {
		t.Fatalf("unexpected event: %+v", ev)
	}
	if ev.Metadata["agentId"] != "agent-1" {
		t.Fatalf("unexpected event metadata: %+v", ev.Metadata)
	}
	if response.NextCursor != 6 {
		t.Fatalf("unexpected nextCursor: %d", response.NextCursor)
	}
}

// TestIntegrationAPIErrorPreservationAcrossFacades verifies that typed API
// errors returned by the backend are consistently exposed through the Agent,
// Tools, and Events facades.
func TestIntegrationAPIErrorPreservationAcrossFacades(t *testing.T) {
	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		writeJSON(w, http.StatusUnauthorized, `{"status":401,"error":"unauthorized","message":"Missing credentials"}`)
	}))
	defer server.Close()

	client, err := NewClient(server.URL)
	if err != nil {
		t.Fatalf("NewClient returned error: %v", err)
	}
	agent, err := NewAgent(client)
	if err != nil {
		t.Fatalf("NewAgent returned error: %v", err)
	}
	tools, err := NewTools(client)
	if err != nil {
		t.Fatalf("NewTools returned error: %v", err)
	}
	events, err := NewEvents(client)
	if err != nil {
		t.Fatalf("NewEvents returned error: %v", err)
	}
	ctx := context.Background()

	checkAPIError := func(label string, err error) {
		t.Helper()
		if err == nil {
			t.Errorf("%s: expected API error", label)
			return
		}
		var apiErr *APIError
		if !errors.As(err, &apiErr) {
			t.Errorf("%s: expected *APIError, got %T: %v", label, err, err)
			return
		}
		if apiErr.StatusCode != http.StatusUnauthorized || apiErr.Code != "unauthorized" || apiErr.Message != "Missing credentials" {
			t.Errorf("%s: unexpected API error fields: %+v", label, apiErr)
		}
		if apiErr.Retryable() {
			t.Errorf("%s: 401 should not be retryable", label)
		}
	}

	_, agentErr := agent.Run(ctx, "hello")
	checkAPIError("agent.Run", agentErr)

	_, toolsErr := tools.List(ctx)
	checkAPIError("tools.List", toolsErr)

	_, eventsErr := events.Poll(ctx, EventPollOptions{})
	checkAPIError("events.Poll", eventsErr)
}

// TestIntegrationSubscribeDeliversEventsUntilCanceled verifies the Events
// subscription loop delivers events from multiple polling rounds in order and
// stops cleanly when the context is canceled.
func TestIntegrationSubscribeDeliversEventsUntilCanceled(t *testing.T) {
	ctx, cancel := context.WithCancel(context.Background())
	defer cancel()

	polls := 0
	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		polls++
		switch polls {
		case 1:
			writeJSON(w, http.StatusOK, `{"events":[{"sequence":1,"type":"TOOL_CALL","timestamp":1000,"source":"agent-1","metadata":{}},{"sequence":2,"type":"AGENT_STATE_CHANGED","timestamp":1001,"source":"agent-1","metadata":{}}],"nextCursor":2}`)
		default:
			writeJSON(w, http.StatusOK, `{"events":[],"nextCursor":2}`)
		}
	}))
	defer server.Close()

	client, err := NewClient(server.URL)
	if err != nil {
		t.Fatalf("NewClient returned error: %v", err)
	}
	events, err := NewEvents(client)
	if err != nil {
		t.Fatalf("NewEvents returned error: %v", err)
	}

	var received []int64
	err = events.Subscribe(ctx, EventSubscribeOptions{PollInterval: time.Millisecond}, func(ctx context.Context, event Event) error {
		received = append(received, event.Sequence)
		if event.Sequence == 2 {
			cancel()
		}
		return nil
	})
	if !errors.Is(err, context.Canceled) {
		t.Fatalf("expected context.Canceled, got %v", err)
	}
	if len(received) != 2 || received[0] != 1 || received[1] != 2 {
		t.Fatalf("unexpected received event sequences: %v", received)
	}
}

// ====== Optional live-backend integration tests ======
//
// These tests require SPECDRIVEN_GO_SDK_BASE_URL to be set to a running Java
// backend. They skip with a clear reason when the environment variable is
// absent. Optional auth can be provided via SPECDRIVEN_GO_SDK_BEARER_TOKEN or
// SPECDRIVEN_GO_SDK_API_KEY.

func TestLiveBackendHealth(t *testing.T) {
	baseURL := os.Getenv("SPECDRIVEN_GO_SDK_BASE_URL")
	if baseURL == "" {
		t.Skip("SPECDRIVEN_GO_SDK_BASE_URL not set; skipping live backend health test")
	}

	client, err := NewClient(baseURL)
	if err != nil {
		t.Fatalf("NewClient returned error: %v", err)
	}

	response, err := client.Health(context.Background())
	if err != nil {
		t.Fatalf("Health returned error: %v", err)
	}
	if response.Status == "" {
		t.Fatal("expected non-empty status from live backend")
	}
	t.Logf("live backend health: status=%q version=%q", response.Status, response.Version)
}

func TestLiveBackendListTools(t *testing.T) {
	baseURL := os.Getenv("SPECDRIVEN_GO_SDK_BASE_URL")
	if baseURL == "" {
		t.Skip("SPECDRIVEN_GO_SDK_BASE_URL not set; skipping live backend list-tools test")
	}

	opts := []Option{}
	if token := os.Getenv("SPECDRIVEN_GO_SDK_BEARER_TOKEN"); token != "" {
		opts = append(opts, WithBearerToken(token))
	} else if key := os.Getenv("SPECDRIVEN_GO_SDK_API_KEY"); key != "" {
		opts = append(opts, WithAPIKey(key))
	}

	client, err := NewClient(baseURL, opts...)
	if err != nil {
		t.Fatalf("NewClient returned error: %v", err)
	}
	tools, err := NewTools(client)
	if err != nil {
		t.Fatalf("NewTools returned error: %v", err)
	}

	response, err := tools.List(context.Background())
	if err != nil {
		var apiErr *APIError
		if errors.As(err, &apiErr) && (apiErr.StatusCode == http.StatusUnauthorized || apiErr.StatusCode == http.StatusForbidden) {
			t.Skipf("live backend requires authentication not provided; set SPECDRIVEN_GO_SDK_BEARER_TOKEN or SPECDRIVEN_GO_SDK_API_KEY: %v", err)
		}
		t.Fatalf("List returned error: %v", err)
	}
	t.Logf("live backend returned %d tools", len(response.Tools))
}
