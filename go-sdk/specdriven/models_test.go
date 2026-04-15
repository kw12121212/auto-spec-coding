package specdriven

import (
	"encoding/json"
	"testing"
)

func TestRunAgentRequestJSONFieldNames(t *testing.T) {
	systemPrompt := "You are helpful"
	maxTurns := 5
	timeout := 20
	body, err := json.Marshal(RunAgentRequest{
		Prompt:             "review",
		SystemPrompt:       &systemPrompt,
		MaxTurns:           &maxTurns,
		ToolTimeoutSeconds: &timeout,
	})
	if err != nil {
		t.Fatalf("Marshal returned error: %v", err)
	}

	var fields map[string]any
	if err := json.Unmarshal(body, &fields); err != nil {
		t.Fatalf("Unmarshal returned error: %v", err)
	}
	if fields["prompt"] != "review" {
		t.Fatalf("unexpected prompt field: %v", fields["prompt"])
	}
	if fields["systemPrompt"] != systemPrompt {
		t.Fatalf("unexpected systemPrompt field: %v", fields["systemPrompt"])
	}
	if fields["maxTurns"] != float64(maxTurns) {
		t.Fatalf("unexpected maxTurns field: %v", fields["maxTurns"])
	}
	if fields["toolTimeoutSeconds"] != float64(timeout) {
		t.Fatalf("unexpected toolTimeoutSeconds field: %v", fields["toolTimeoutSeconds"])
	}
}

func TestRunAgentResponseAllowsNullOutput(t *testing.T) {
	var response RunAgentResponse
	if err := json.Unmarshal([]byte(`{"agentId":"agent-1","output":null,"state":"RUNNING"}`), &response); err != nil {
		t.Fatalf("Unmarshal returned error: %v", err)
	}
	if response.AgentID != "agent-1" || response.State != "RUNNING" {
		t.Fatalf("unexpected response: %+v", response)
	}
	if response.Output != nil {
		t.Fatalf("expected nil output, got %q", *response.Output)
	}
}

func TestToolsListResponseDecodesEmptyList(t *testing.T) {
	var response ToolsListResponse
	if err := json.Unmarshal([]byte(`{"tools":[]}`), &response); err != nil {
		t.Fatalf("Unmarshal returned error: %v", err)
	}
	if len(response.Tools) != 0 {
		t.Fatalf("expected empty tools, got %d", len(response.Tools))
	}
}
