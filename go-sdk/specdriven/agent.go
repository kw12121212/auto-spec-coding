package specdriven

import (
	"context"
	"errors"
	"strings"
)

// Agent provides high-level agent operations backed by a Client.
type Agent struct {
	client *Client
}

// RunOption customizes an Agent run request.
type RunOption func(*RunAgentRequest)

// NewAgent creates an Agent handle backed by an existing Client.
func NewAgent(client *Client) (*Agent, error) {
	if client == nil {
		return nil, errors.New("specdriven: client must not be nil")
	}
	return &Agent{client: client}, nil
}

// WithSystemPrompt configures the system prompt for a run request.
func WithSystemPrompt(systemPrompt string) RunOption {
	return func(request *RunAgentRequest) {
		request.SystemPrompt = &systemPrompt
	}
}

// WithMaxTurns configures the max turns for a run request.
func WithMaxTurns(maxTurns int) RunOption {
	return func(request *RunAgentRequest) {
		request.MaxTurns = &maxTurns
	}
}

// WithToolTimeoutSeconds configures the tool timeout seconds for a run request.
func WithToolTimeoutSeconds(timeoutSeconds int) RunOption {
	return func(request *RunAgentRequest) {
		request.ToolTimeoutSeconds = &timeoutSeconds
	}
}

// Run sends a prompt through the backend agent run endpoint.
func (a *Agent) Run(ctx context.Context, prompt string, opts ...RunOption) (*RunAgentResponse, error) {
	if strings.TrimSpace(prompt) == "" {
		return nil, errors.New("specdriven: prompt must not be empty")
	}
	request := RunAgentRequest{Prompt: prompt}
	for _, opt := range opts {
		if opt != nil {
			opt(&request)
		}
	}
	return a.client.RunAgent(ctx, request)
}

// Stop stops an existing backend agent.
func (a *Agent) Stop(ctx context.Context, agentID string) error {
	return a.client.StopAgent(ctx, agentID)
}

// State returns the current backend state for an agent.
func (a *Agent) State(ctx context.Context, agentID string) (*AgentStateResponse, error) {
	return a.client.GetAgentState(ctx, agentID)
}
