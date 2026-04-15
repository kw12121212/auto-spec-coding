package specdriven

import (
	"context"
	"encoding/json"
	"net/http"
	"net/http/httptest"
	"testing"
	"time"
)

func TestNewClientRejectsMissingBaseURL(t *testing.T) {
	_, err := NewClient(" ")
	if err == nil {
		t.Fatal("expected missing base URL error")
	}
}

func TestClientUsesCallerProvidedHTTPClient(t *testing.T) {
	transport := roundTripFunc(func(req *http.Request) (*http.Response, error) {
		if req.URL.String() != "http://example.com/api/v1/health" {
			t.Fatalf("unexpected URL: %s", req.URL.String())
		}
		return jsonResponse(http.StatusOK, `{"status":"ok","version":"0.1.0"}`), nil
	})
	client, err := NewClient("http://example.com", WithHTTPClient(&http.Client{Transport: transport}))
	if err != nil {
		t.Fatalf("NewClient returned error: %v", err)
	}

	health, err := client.Health(context.Background())
	if err != nil {
		t.Fatalf("Health returned error: %v", err)
	}
	if health.Status != "ok" || health.Version != "0.1.0" {
		t.Fatalf("unexpected health response: %+v", health)
	}
}

func TestHealthSendsNoAuthHeader(t *testing.T) {
	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		if r.Method != http.MethodGet {
			t.Fatalf("unexpected method: %s", r.Method)
		}
		if r.URL.Path != "/api/v1/health" {
			t.Fatalf("unexpected path: %s", r.URL.Path)
		}
		if got := r.Header.Get("Authorization"); got != "" {
			t.Fatalf("unexpected authorization header: %q", got)
		}
		if got := r.Header.Get("X-API-Key"); got != "" {
			t.Fatalf("unexpected api key header: %q", got)
		}
		writeJSON(w, http.StatusOK, `{"status":"ok","version":"0.1.0"}`)
	}))
	defer server.Close()

	client, err := NewClient(server.URL)
	if err != nil {
		t.Fatalf("NewClient returned error: %v", err)
	}
	if _, err := client.Health(context.Background()); err != nil {
		t.Fatalf("Health returned error: %v", err)
	}
}

func TestListToolsSendsBearerTokenAndDecodesTools(t *testing.T) {
	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		if r.Method != http.MethodGet {
			t.Fatalf("unexpected method: %s", r.Method)
		}
		if r.URL.Path != "/api/v1/tools" {
			t.Fatalf("unexpected path: %s", r.URL.Path)
		}
		if got := r.Header.Get("Authorization"); got != "Bearer token-1" {
			t.Fatalf("unexpected bearer token: %q", got)
		}
		writeJSON(w, http.StatusOK, `{"tools":[{"name":"bash","description":"run commands","parameters":[{"name":"command","type":"string"}]}]}`)
	}))
	defer server.Close()

	client, err := NewClient(server.URL, WithBearerToken("token-1"))
	if err != nil {
		t.Fatalf("NewClient returned error: %v", err)
	}
	tools, err := client.ListTools(context.Background())
	if err != nil {
		t.Fatalf("ListTools returned error: %v", err)
	}
	if len(tools.Tools) != 1 {
		t.Fatalf("expected one tool, got %d", len(tools.Tools))
	}
	if tools.Tools[0].Name != "bash" || tools.Tools[0].Description != "run commands" {
		t.Fatalf("unexpected tool: %+v", tools.Tools[0])
	}
	if got := tools.Tools[0].Parameters[0]["name"]; got != "command" {
		t.Fatalf("unexpected parameter name: %v", got)
	}
}

func TestRunAgentSendsAPIKeyAndRequestBody(t *testing.T) {
	systemPrompt := "You are a reviewer"
	maxTurns := 10
	timeout := 30
	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		if r.Method != http.MethodPost {
			t.Fatalf("unexpected method: %s", r.Method)
		}
		if r.URL.Path != "/api/v1/agent/run" {
			t.Fatalf("unexpected path: %s", r.URL.Path)
		}
		if got := r.Header.Get("X-API-Key"); got != "api-key-1" {
			t.Fatalf("unexpected api key: %q", got)
		}
		var body RunAgentRequest
		if err := json.NewDecoder(r.Body).Decode(&body); err != nil {
			t.Fatalf("failed to decode request body: %v", err)
		}
		if body.Prompt != "hello" {
			t.Fatalf("unexpected prompt: %q", body.Prompt)
		}
		if body.SystemPrompt == nil || *body.SystemPrompt != systemPrompt {
			t.Fatalf("unexpected system prompt: %v", body.SystemPrompt)
		}
		if body.MaxTurns == nil || *body.MaxTurns != maxTurns {
			t.Fatalf("unexpected max turns: %v", body.MaxTurns)
		}
		if body.ToolTimeoutSeconds == nil || *body.ToolTimeoutSeconds != timeout {
			t.Fatalf("unexpected tool timeout: %v", body.ToolTimeoutSeconds)
		}
		writeJSON(w, http.StatusOK, `{"agentId":"agent-1","output":"done","state":"STOPPED"}`)
	}))
	defer server.Close()

	client, err := NewClient(server.URL, WithAPIKey("api-key-1"))
	if err != nil {
		t.Fatalf("NewClient returned error: %v", err)
	}
	response, err := client.RunAgent(context.Background(), RunAgentRequest{
		Prompt:             "hello",
		SystemPrompt:       &systemPrompt,
		MaxTurns:           &maxTurns,
		ToolTimeoutSeconds: &timeout,
	})
	if err != nil {
		t.Fatalf("RunAgent returned error: %v", err)
	}
	if response.AgentID != "agent-1" || response.Output == nil || *response.Output != "done" || response.State != "STOPPED" {
		t.Fatalf("unexpected run response: %+v", response)
	}
}

func TestStopAgentAndGetAgentStateUseEscapedID(t *testing.T) {
	requests := 0
	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		requests++
		if got := r.URL.Query().Get("id"); got != "agent 1" {
			t.Fatalf("unexpected agent id: %q", got)
		}
		switch r.URL.Path {
		case "/api/v1/agent/stop":
			if r.Method != http.MethodPost {
				t.Fatalf("unexpected stop method: %s", r.Method)
			}
			w.WriteHeader(http.StatusOK)
		case "/api/v1/agent/state":
			if r.Method != http.MethodGet {
				t.Fatalf("unexpected state method: %s", r.Method)
			}
			writeJSON(w, http.StatusOK, `{"agentId":"agent 1","state":"STOPPED","createdAt":1000,"updatedAt":2000}`)
		default:
			t.Fatalf("unexpected path: %s", r.URL.Path)
		}
	}))
	defer server.Close()

	client, err := NewClient(server.URL, WithBearerToken("token"))
	if err != nil {
		t.Fatalf("NewClient returned error: %v", err)
	}
	if err := client.StopAgent(context.Background(), "agent 1"); err != nil {
		t.Fatalf("StopAgent returned error: %v", err)
	}
	state, err := client.GetAgentState(context.Background(), "agent 1")
	if err != nil {
		t.Fatalf("GetAgentState returned error: %v", err)
	}
	if state.AgentID != "agent 1" || state.State != "STOPPED" || state.CreatedAt != 1000 || state.UpdatedAt != 2000 {
		t.Fatalf("unexpected state response: %+v", state)
	}
	if requests != 2 {
		t.Fatalf("expected two requests, got %d", requests)
	}
}

func TestOptionsValidateInputs(t *testing.T) {
	cases := []struct {
		name string
		opt  Option
	}{
		{name: "nil http client", opt: WithHTTPClient(nil)},
		{name: "zero timeout", opt: WithTimeout(0)},
		{name: "empty bearer", opt: WithBearerToken("")},
		{name: "empty api key", opt: WithAPIKey("")},
		{name: "negative retries", opt: WithRetry(-1, time.Millisecond)},
		{name: "negative wait", opt: WithRetry(1, -time.Millisecond)},
		{name: "empty user agent", opt: WithUserAgent(" ")},
	}
	for _, tc := range cases {
		t.Run(tc.name, func(t *testing.T) {
			if _, err := NewClient("http://example.com", tc.opt); err == nil {
				t.Fatal("expected validation error")
			}
		})
	}
}

func writeJSON(w http.ResponseWriter, status int, body string) {
	w.Header().Set("Content-Type", "application/json")
	w.WriteHeader(status)
	_, _ = w.Write([]byte(body))
}
