package specdriven

// HealthResponse represents GET /api/v1/health.
type HealthResponse struct {
	Status  string `json:"status"`
	Version string `json:"version"`
}

// ToolInfo represents one tool returned by GET /api/v1/tools.
type ToolInfo struct {
	Name        string           `json:"name"`
	Description string           `json:"description"`
	Parameters  []map[string]any `json:"parameters"`
}

// ToolsListResponse represents GET /api/v1/tools.
type ToolsListResponse struct {
	Tools []ToolInfo `json:"tools"`
}

// RunAgentRequest represents POST /api/v1/agent/run.
type RunAgentRequest struct {
	Prompt             string  `json:"prompt"`
	SystemPrompt       *string `json:"systemPrompt,omitempty"`
	MaxTurns           *int    `json:"maxTurns,omitempty"`
	ToolTimeoutSeconds *int    `json:"toolTimeoutSeconds,omitempty"`
}

// RunAgentResponse represents POST /api/v1/agent/run.
type RunAgentResponse struct {
	AgentID string  `json:"agentId"`
	Output  *string `json:"output"`
	State   string  `json:"state"`
}

// AgentStateResponse represents GET /api/v1/agent/state.
type AgentStateResponse struct {
	AgentID   string `json:"agentId"`
	State     string `json:"state"`
	CreatedAt int64  `json:"createdAt"`
	UpdatedAt int64  `json:"updatedAt"`
}

type errorResponse struct {
	Status  int    `json:"status"`
	Error   string `json:"error"`
	Message string `json:"message"`
}
