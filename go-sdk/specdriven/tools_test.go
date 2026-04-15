package specdriven

import (
	"context"
	"encoding/json"
	"errors"
	"net/http"
	"net/http/httptest"
	"strings"
	"testing"
)

func TestNewToolsRejectsNilClient(t *testing.T) {
	tools, err := NewTools(nil)
	if err == nil {
		t.Fatal("expected nil client error")
	}
	if tools != nil {
		t.Fatalf("expected nil tools, got %+v", tools)
	}
}

func TestToolsListDelegatesToClient(t *testing.T) {
	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		if r.Method != http.MethodGet {
			t.Fatalf("unexpected method: %s", r.Method)
		}
		if r.URL.Path != "/api/v1/tools" {
			t.Fatalf("unexpected path: %s", r.URL.Path)
		}
		writeJSON(w, http.StatusOK, `{"tools":[{"name":"bash","description":"run","parameters":[]}]}`)
	}))
	defer server.Close()

	tools := newTestTools(t, server.URL)
	response, err := tools.List(context.Background())
	if err != nil {
		t.Fatalf("List returned error: %v", err)
	}
	if len(response.Tools) != 1 || response.Tools[0].Name != "bash" {
		t.Fatalf("unexpected tools response: %+v", response)
	}
}

func TestRegisterRemoteSendsRequestBodyAndAuth(t *testing.T) {
	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		if r.Method != http.MethodPost {
			t.Fatalf("unexpected method: %s", r.Method)
		}
		if r.URL.Path != "/api/v1/tools/register" {
			t.Fatalf("unexpected path: %s", r.URL.Path)
		}
		if got := r.Header.Get("Authorization"); got != "Bearer token-1" {
			t.Fatalf("unexpected auth header: %q", got)
		}
		var body RemoteToolRegistrationRequest
		if err := json.NewDecoder(r.Body).Decode(&body); err != nil {
			t.Fatalf("decode request: %v", err)
		}
		if body.Name != "lookup" || body.Description != "lookup data" || body.CallbackURL != "http://localhost/tool" {
			t.Fatalf("unexpected registration body: %+v", body)
		}
		if len(body.Parameters) != 1 || body.Parameters[0]["name"] != "term" {
			t.Fatalf("unexpected parameters: %+v", body.Parameters)
		}
		writeJSON(w, http.StatusOK, `{"name":"lookup","description":"lookup data","parameters":[{"name":"term","type":"string","description":"search term","required":true}]}`)
	}))
	defer server.Close()

	client, err := NewClient(server.URL, WithBearerToken("token-1"))
	if err != nil {
		t.Fatalf("NewClient returned error: %v", err)
	}
	tools, err := NewTools(client)
	if err != nil {
		t.Fatalf("NewTools returned error: %v", err)
	}
	registered, err := tools.RegisterRemote(context.Background(), RemoteToolRegistrationRequest{
		Name:        "lookup",
		Description: "lookup data",
		CallbackURL: "http://localhost/tool",
		Parameters: []map[string]any{
			{"name": "term", "type": "string", "description": "search term", "required": true},
		},
	})
	if err != nil {
		t.Fatalf("RegisterRemote returned error: %v", err)
	}
	if registered.Name != "lookup" || len(registered.Parameters) != 1 {
		t.Fatalf("unexpected registered tool: %+v", registered)
	}
}

func TestRegisterRemoteValidatesRequiredFields(t *testing.T) {
	tools := newTestTools(t, "http://example.com")
	if _, err := tools.RegisterRemote(context.Background(), RemoteToolRegistrationRequest{CallbackURL: "http://localhost/tool"}); err == nil {
		t.Fatal("expected missing name error")
	}
	if _, err := tools.RegisterRemote(context.Background(), RemoteToolRegistrationRequest{Name: "lookup"}); err == nil {
		t.Fatal("expected missing callback URL error")
	}
}

func TestRegisterRemotePreservesAPIErrors(t *testing.T) {
	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		writeJSON(w, http.StatusConflict, `{"status":409,"error":"conflict","message":"Tool already exists"}`)
	}))
	defer server.Close()

	tools := newTestTools(t, server.URL)
	_, err := tools.RegisterRemote(context.Background(), RemoteToolRegistrationRequest{
		Name:        "bash",
		CallbackURL: "http://localhost/tool",
	})
	if err == nil {
		t.Fatal("expected API error")
	}
	var apiErr *APIError
	if !errors.As(err, &apiErr) {
		t.Fatalf("expected APIError, got %T", err)
	}
	if apiErr.StatusCode != http.StatusConflict || apiErr.Code != "conflict" {
		t.Fatalf("unexpected API error: %+v", apiErr)
	}
}

func TestRegisterRemoteRespectsCanceledContext(t *testing.T) {
	tools := newTestTools(t, "http://example.com")
	ctx, cancel := context.WithCancel(context.Background())
	cancel()
	_, err := tools.RegisterRemote(ctx, RemoteToolRegistrationRequest{
		Name:        "lookup",
		CallbackURL: "http://localhost/tool",
	})
	if !errors.Is(err, context.Canceled) {
		t.Fatalf("expected canceled context, got %v", err)
	}
}

func TestToolCallbackHandlerSuccess(t *testing.T) {
	handler := NewToolCallbackHandler()
	ctxKey := struct{}{}
	if err := handler.Register("lookup", func(ctx context.Context, parameters map[string]any) (string, error) {
		if ctx.Value(ctxKey) != "request-value" {
			t.Fatalf("request context was not passed")
		}
		if parameters["term"] != "abc" {
			t.Fatalf("unexpected parameters: %+v", parameters)
		}
		return "found", nil
	}); err != nil {
		t.Fatalf("Register returned error: %v", err)
	}

	req := httptest.NewRequest(http.MethodPost, "/tool", stringsReader(`{"toolName":"lookup","parameters":{"term":"abc"}}`))
	req = req.WithContext(context.WithValue(req.Context(), ctxKey, "request-value"))
	resp := httptest.NewRecorder()
	handler.ServeHTTP(resp, req)

	if resp.Code != http.StatusOK {
		t.Fatalf("unexpected status: %d", resp.Code)
	}
	var body RemoteToolInvocationResponse
	if err := json.NewDecoder(resp.Body).Decode(&body); err != nil {
		t.Fatalf("decode response: %v", err)
	}
	if !body.Success || body.Output != "found" {
		t.Fatalf("unexpected callback response: %+v", body)
	}
}

func TestToolCallbackHandlerUnknownTool(t *testing.T) {
	handler := NewToolCallbackHandler()
	resp := httptest.NewRecorder()
	handler.ServeHTTP(resp, httptest.NewRequest(http.MethodPost, "/tool", stringsReader(`{"toolName":"missing","parameters":{}}`)))

	var body RemoteToolInvocationResponse
	if err := json.NewDecoder(resp.Body).Decode(&body); err != nil {
		t.Fatalf("decode response: %v", err)
	}
	if body.Success || body.Error == "" {
		t.Fatalf("expected tool error response, got %+v", body)
	}
}

func TestToolCallbackHandlerInvalidJSON(t *testing.T) {
	handler := NewToolCallbackHandler()
	resp := httptest.NewRecorder()
	handler.ServeHTTP(resp, httptest.NewRequest(http.MethodPost, "/tool", stringsReader(`{bad json`)))

	if resp.Code != http.StatusBadRequest {
		t.Fatalf("expected 400, got %d", resp.Code)
	}
}

func TestToolCallbackHandlerPreservesHandlerError(t *testing.T) {
	handler := NewToolCallbackHandler()
	if err := handler.Register("lookup", func(context.Context, map[string]any) (string, error) {
		return "", errors.New("lookup failed")
	}); err != nil {
		t.Fatalf("Register returned error: %v", err)
	}
	resp := httptest.NewRecorder()
	handler.ServeHTTP(resp, httptest.NewRequest(http.MethodPost, "/tool", stringsReader(`{"toolName":"lookup","parameters":{}}`)))

	var body RemoteToolInvocationResponse
	if err := json.NewDecoder(resp.Body).Decode(&body); err != nil {
		t.Fatalf("decode response: %v", err)
	}
	if body.Success || body.Error != "lookup failed" {
		t.Fatalf("unexpected callback response: %+v", body)
	}
}

func newTestTools(t *testing.T, baseURL string) *Tools {
	t.Helper()
	client, err := NewClient(baseURL)
	if err != nil {
		t.Fatalf("NewClient returned error: %v", err)
	}
	tools, err := NewTools(client)
	if err != nil {
		t.Fatalf("NewTools returned error: %v", err)
	}
	return tools
}

func stringsReader(s string) *strings.Reader {
	return strings.NewReader(s)
}
