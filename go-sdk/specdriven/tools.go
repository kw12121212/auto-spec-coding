package specdriven

import (
	"context"
	"encoding/json"
	"errors"
	"net/http"
	"strings"
	"sync"
)

// RemoteToolRegistrationRequest registers a callback-backed tool with the Java backend.
type RemoteToolRegistrationRequest struct {
	Name        string           `json:"name"`
	Description string           `json:"description"`
	Parameters  []map[string]any `json:"parameters"`
	CallbackURL string           `json:"callbackUrl"`
}

func (r RemoteToolRegistrationRequest) validate() error {
	if strings.TrimSpace(r.Name) == "" {
		return errors.New("specdriven: tool name must not be empty")
	}
	if strings.TrimSpace(r.CallbackURL) == "" {
		return errors.New("specdriven: tool callback URL must not be empty")
	}
	return nil
}

// RemoteToolInvocationRequest is sent by the Java backend to a Go tool callback.
type RemoteToolInvocationRequest struct {
	ToolName   string         `json:"toolName"`
	Parameters map[string]any `json:"parameters"`
}

// RemoteToolInvocationResponse is returned by a Go tool callback handler.
type RemoteToolInvocationResponse struct {
	Success bool   `json:"success"`
	Output  string `json:"output,omitempty"`
	Error   string `json:"error,omitempty"`
}

// ToolHandler implements a Go-owned tool exposed through callback registration.
type ToolHandler func(context.Context, map[string]any) (string, error)

// Tools provides high-level tool operations backed by a Client.
type Tools struct {
	client *Client
}

// NewTools creates a Tools handle backed by an existing Client.
func NewTools(client *Client) (*Tools, error) {
	if client == nil {
		return nil, errors.New("specdriven: client must not be nil")
	}
	return &Tools{client: client}, nil
}

// List returns tools visible through the Java backend.
func (t *Tools) List(ctx context.Context) (*ToolsListResponse, error) {
	return t.client.ListTools(ctx)
}

// RegisterRemote registers a callback-backed Go tool with the Java backend.
func (t *Tools) RegisterRemote(ctx context.Context, request RemoteToolRegistrationRequest) (*ToolInfo, error) {
	return t.client.RegisterRemoteTool(ctx, request)
}

// ToolCallbackHandler dispatches backend tool callback requests to local Go handlers.
type ToolCallbackHandler struct {
	mu       sync.RWMutex
	handlers map[string]ToolHandler
}

// NewToolCallbackHandler creates an empty callback handler registry.
func NewToolCallbackHandler() *ToolCallbackHandler {
	return &ToolCallbackHandler{handlers: make(map[string]ToolHandler)}
}

// Register adds or replaces a local Go tool handler.
func (h *ToolCallbackHandler) Register(name string, handler ToolHandler) error {
	if strings.TrimSpace(name) == "" {
		return errors.New("specdriven: tool name must not be empty")
	}
	if handler == nil {
		return errors.New("specdriven: tool handler must not be nil")
	}
	h.mu.Lock()
	defer h.mu.Unlock()
	h.handlers[strings.TrimSpace(name)] = handler
	return nil
}

// ServeHTTP handles Java backend remote tool invocation callbacks.
func (h *ToolCallbackHandler) ServeHTTP(w http.ResponseWriter, r *http.Request) {
	w.Header().Set("Content-Type", "application/json")
	if r.Method != http.MethodPost {
		w.WriteHeader(http.StatusMethodNotAllowed)
		_ = json.NewEncoder(w).Encode(RemoteToolInvocationResponse{
			Success: false,
			Error:   "method not allowed",
		})
		return
	}

	var request RemoteToolInvocationRequest
	if err := json.NewDecoder(r.Body).Decode(&request); err != nil {
		w.WriteHeader(http.StatusBadRequest)
		_ = json.NewEncoder(w).Encode(RemoteToolInvocationResponse{
			Success: false,
			Error:   "invalid tool invocation request",
		})
		return
	}
	name := strings.TrimSpace(request.ToolName)
	if name == "" {
		writeToolError(w, "toolName must not be empty")
		return
	}

	h.mu.RLock()
	handler := h.handlers[name]
	h.mu.RUnlock()
	if handler == nil {
		writeToolError(w, "tool not found: "+name)
		return
	}

	parameters := request.Parameters
	if parameters == nil {
		parameters = map[string]any{}
	}
	output, err := handler(r.Context(), parameters)
	if err != nil {
		writeToolError(w, err.Error())
		return
	}
	_ = json.NewEncoder(w).Encode(RemoteToolInvocationResponse{
		Success: true,
		Output:  output,
	})
}

func writeToolError(w http.ResponseWriter, message string) {
	_ = json.NewEncoder(w).Encode(RemoteToolInvocationResponse{
		Success: false,
		Error:   message,
	})
}
