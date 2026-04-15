package specdriven

import (
	"context"
	"encoding/json"
	"errors"
	"net/http"
	"net/http/httptest"
	"testing"
)

func TestNewAgentRejectsNilClient(t *testing.T) {
	agent, err := NewAgent(nil)
	if err == nil {
		t.Fatal("expected nil client error")
	}
	if agent != nil {
		t.Fatalf("expected nil agent, got %+v", agent)
	}
}

func TestNewAgentConstructsAgentFromClient(t *testing.T) {
	client, err := NewClient("http://example.com")
	if err != nil {
		t.Fatalf("NewClient returned error: %v", err)
	}
	agent, err := NewAgent(client)
	if err != nil {
		t.Fatalf("NewAgent returned error: %v", err)
	}
	if agent == nil {
		t.Fatal("expected agent")
	}
}

func TestAgentRunSendsPromptAndOptions(t *testing.T) {
	systemPrompt := "You are a reviewer"
	maxTurns := 8
	timeoutSeconds := 45
	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		if r.Method != http.MethodPost {
			t.Fatalf("unexpected method: %s", r.Method)
		}
		if r.URL.Path != "/api/v1/agent/run" {
			t.Fatalf("unexpected path: %s", r.URL.Path)
		}
		var body RunAgentRequest
		if err := json.NewDecoder(r.Body).Decode(&body); err != nil {
			t.Fatalf("failed to decode request body: %v", err)
		}
		if body.Prompt != " review this " {
			t.Fatalf("unexpected prompt: %q", body.Prompt)
		}
		if body.SystemPrompt == nil || *body.SystemPrompt != systemPrompt {
			t.Fatalf("unexpected system prompt: %v", body.SystemPrompt)
		}
		if body.MaxTurns == nil || *body.MaxTurns != maxTurns {
			t.Fatalf("unexpected max turns: %v", body.MaxTurns)
		}
		if body.ToolTimeoutSeconds == nil || *body.ToolTimeoutSeconds != timeoutSeconds {
			t.Fatalf("unexpected tool timeout: %v", body.ToolTimeoutSeconds)
		}
		writeJSON(w, http.StatusOK, `{"agentId":"agent-1","output":"done","state":"STOPPED"}`)
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
	response, err := agent.Run(
		context.Background(),
		" review this ",
		WithSystemPrompt(systemPrompt),
		WithMaxTurns(maxTurns),
		WithToolTimeoutSeconds(timeoutSeconds),
	)
	if err != nil {
		t.Fatalf("Run returned error: %v", err)
	}
	if response.AgentID != "agent-1" || response.Output == nil || *response.Output != "done" || response.State != "STOPPED" {
		t.Fatalf("unexpected run response: %+v", response)
	}
}

func TestAgentRunRejectsEmptyPrompt(t *testing.T) {
	requests := 0
	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		requests++
		w.WriteHeader(http.StatusOK)
	}))
	defer server.Close()

	agent := newTestAgent(t, server.URL)
	if _, err := agent.Run(context.Background(), " \t "); err == nil {
		t.Fatal("expected empty prompt error")
	}
	if requests != 0 {
		t.Fatalf("expected no requests, got %d", requests)
	}
}

func TestAgentRunPreservesAPIErrors(t *testing.T) {
	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		writeJSON(w, http.StatusBadRequest, `{"status":400,"error":"invalid_prompt","message":"Prompt is invalid"}`)
	}))
	defer server.Close()

	agent := newTestAgent(t, server.URL)
	_, err := agent.Run(context.Background(), "bad")
	if err == nil {
		t.Fatal("expected API error")
	}
	var apiErr *APIError
	if !errors.As(err, &apiErr) {
		t.Fatalf("expected APIError, got %T", err)
	}
	if apiErr.StatusCode != http.StatusBadRequest || apiErr.Code != "invalid_prompt" || apiErr.Message != "Prompt is invalid" {
		t.Fatalf("unexpected API error: %+v", apiErr)
	}
}

func TestAgentStopAndStateDelegateAgentID(t *testing.T) {
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
			writeJSON(w, http.StatusOK, `{"agentId":"agent 1","state":"RUNNING","createdAt":1000,"updatedAt":2000}`)
		default:
			t.Fatalf("unexpected path: %s", r.URL.Path)
		}
	}))
	defer server.Close()

	agent := newTestAgent(t, server.URL)
	if err := agent.Stop(context.Background(), "agent 1"); err != nil {
		t.Fatalf("Stop returned error: %v", err)
	}
	state, err := agent.State(context.Background(), "agent 1")
	if err != nil {
		t.Fatalf("State returned error: %v", err)
	}
	if state.AgentID != "agent 1" || state.State != "RUNNING" || state.CreatedAt != 1000 || state.UpdatedAt != 2000 {
		t.Fatalf("unexpected state response: %+v", state)
	}
	if requests != 2 {
		t.Fatalf("expected two requests, got %d", requests)
	}
}

func TestAgentStopAndStateRejectEmptyAgentID(t *testing.T) {
	requests := 0
	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		requests++
		w.WriteHeader(http.StatusOK)
	}))
	defer server.Close()

	agent := newTestAgent(t, server.URL)
	if err := agent.Stop(context.Background(), " "); err == nil {
		t.Fatal("expected stop validation error")
	}
	if _, err := agent.State(context.Background(), " "); err == nil {
		t.Fatal("expected state validation error")
	}
	if requests != 0 {
		t.Fatalf("expected no requests, got %d", requests)
	}
}

func TestAgentMethodsRespectCanceledContext(t *testing.T) {
	agent := newTestAgent(t, "http://example.com")
	ctx, cancel := context.WithCancel(context.Background())
	cancel()

	if _, err := agent.Run(ctx, "hello"); !errors.Is(err, context.Canceled) {
		t.Fatalf("expected canceled run context, got %v", err)
	}
	if err := agent.Stop(ctx, "agent-1"); !errors.Is(err, context.Canceled) {
		t.Fatalf("expected canceled stop context, got %v", err)
	}
	if _, err := agent.State(ctx, "agent-1"); !errors.Is(err, context.Canceled) {
		t.Fatalf("expected canceled state context, got %v", err)
	}
}

func newTestAgent(t *testing.T, baseURL string) *Agent {
	t.Helper()
	client, err := NewClient(baseURL)
	if err != nil {
		t.Fatalf("NewClient returned error: %v", err)
	}
	agent, err := NewAgent(client)
	if err != nil {
		t.Fatalf("NewAgent returned error: %v", err)
	}
	return agent
}
