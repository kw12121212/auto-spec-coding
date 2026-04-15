package specdriven

import (
	"bytes"
	"context"
	"encoding/json"
	"errors"
	"fmt"
	"io"
	"net/http"
	"net/url"
	"strings"
	"time"
)

const defaultUserAgent = "specdriven-go-sdk/0.1.0"

type authMode int

const (
	authNone authMode = iota
	authBearer
	authAPIKey
)

// Client calls a SpecDriven Java backend through its HTTP REST API.
type Client struct {
	baseURL    *url.URL
	httpClient *http.Client
	authMode   authMode
	authValue  string
	retries    int
	retryWait  time.Duration
	userAgent  string
}

// Option customizes a Client.
type Option func(*Client) error

// NewClient creates a client rooted at the backend base URL.
func NewClient(baseURL string, opts ...Option) (*Client, error) {
	trimmed := strings.TrimSpace(baseURL)
	if trimmed == "" {
		return nil, errors.New("specdriven: base URL must not be empty")
	}
	parsed, err := url.Parse(trimmed)
	if err != nil {
		return nil, fmt.Errorf("specdriven: invalid base URL: %w", err)
	}
	if parsed.Scheme == "" || parsed.Host == "" {
		return nil, errors.New("specdriven: base URL must include scheme and host")
	}

	c := &Client{
		baseURL:    parsed,
		httpClient: &http.Client{Timeout: 30 * time.Second},
		retries:    0,
		retryWait:  100 * time.Millisecond,
		userAgent:  defaultUserAgent,
	}
	for _, opt := range opts {
		if opt == nil {
			continue
		}
		if err := opt(c); err != nil {
			return nil, err
		}
	}
	return c, nil
}

// WithHTTPClient configures the HTTP client used for requests.
func WithHTTPClient(httpClient *http.Client) Option {
	return func(c *Client) error {
		if httpClient == nil {
			return errors.New("specdriven: HTTP client must not be nil")
		}
		c.httpClient = httpClient
		return nil
	}
}

// WithTimeout configures the default HTTP client timeout.
func WithTimeout(timeout time.Duration) Option {
	return func(c *Client) error {
		if timeout <= 0 {
			return errors.New("specdriven: timeout must be positive")
		}
		if c.httpClient == nil {
			c.httpClient = &http.Client{}
		}
		copied := *c.httpClient
		copied.Timeout = timeout
		c.httpClient = &copied
		return nil
	}
}

// WithBearerToken sends Authorization: Bearer <token> on authenticated requests.
func WithBearerToken(token string) Option {
	return func(c *Client) error {
		token = strings.TrimSpace(token)
		if token == "" {
			return errors.New("specdriven: bearer token must not be empty")
		}
		c.authMode = authBearer
		c.authValue = token
		return nil
	}
}

// WithAPIKey sends X-API-Key: <key> on authenticated requests.
func WithAPIKey(key string) Option {
	return func(c *Client) error {
		key = strings.TrimSpace(key)
		if key == "" {
			return errors.New("specdriven: API key must not be empty")
		}
		c.authMode = authAPIKey
		c.authValue = key
		return nil
	}
}

// WithRetry configures retry count and wait duration for retryable failures.
func WithRetry(retries int, wait time.Duration) Option {
	return func(c *Client) error {
		if retries < 0 {
			return errors.New("specdriven: retries must be >= 0")
		}
		if wait < 0 {
			return errors.New("specdriven: retry wait must be >= 0")
		}
		c.retries = retries
		c.retryWait = wait
		return nil
	}
}

// WithUserAgent configures the User-Agent header.
func WithUserAgent(userAgent string) Option {
	return func(c *Client) error {
		userAgent = strings.TrimSpace(userAgent)
		if userAgent == "" {
			return errors.New("specdriven: user agent must not be empty")
		}
		c.userAgent = userAgent
		return nil
	}
}

// Health calls GET /api/v1/health.
func (c *Client) Health(ctx context.Context) (*HealthResponse, error) {
	var response HealthResponse
	if err := c.do(ctx, http.MethodGet, "/health", nil, false, &response); err != nil {
		return nil, err
	}
	return &response, nil
}

// ListTools calls GET /api/v1/tools.
func (c *Client) ListTools(ctx context.Context) (*ToolsListResponse, error) {
	var response ToolsListResponse
	if err := c.do(ctx, http.MethodGet, "/tools", nil, true, &response); err != nil {
		return nil, err
	}
	if response.Tools == nil {
		response.Tools = []ToolInfo{}
	}
	return &response, nil
}

// RegisterRemoteTool calls POST /api/v1/tools/register.
func (c *Client) RegisterRemoteTool(ctx context.Context, request RemoteToolRegistrationRequest) (*ToolInfo, error) {
	if err := request.validate(); err != nil {
		return nil, err
	}
	var response ToolInfo
	if err := c.do(ctx, http.MethodPost, "/tools/register", request, true, &response); err != nil {
		return nil, err
	}
	if response.Parameters == nil {
		response.Parameters = []map[string]any{}
	}
	return &response, nil
}

// RunAgent calls POST /api/v1/agent/run.
func (c *Client) RunAgent(ctx context.Context, request RunAgentRequest) (*RunAgentResponse, error) {
	var response RunAgentResponse
	if err := c.do(ctx, http.MethodPost, "/agent/run", request, true, &response); err != nil {
		return nil, err
	}
	return &response, nil
}

// StopAgent calls POST /api/v1/agent/stop?id=<agentId>.
func (c *Client) StopAgent(ctx context.Context, agentID string) error {
	agentID = strings.TrimSpace(agentID)
	if agentID == "" {
		return errors.New("specdriven: agent ID must not be empty")
	}
	path := "/agent/stop?id=" + url.QueryEscape(agentID)
	return c.do(ctx, http.MethodPost, path, nil, true, nil)
}

// GetAgentState calls GET /api/v1/agent/state?id=<agentId>.
func (c *Client) GetAgentState(ctx context.Context, agentID string) (*AgentStateResponse, error) {
	agentID = strings.TrimSpace(agentID)
	if agentID == "" {
		return nil, errors.New("specdriven: agent ID must not be empty")
	}
	path := "/agent/state?id=" + url.QueryEscape(agentID)
	var response AgentStateResponse
	if err := c.do(ctx, http.MethodGet, path, nil, true, &response); err != nil {
		return nil, err
	}
	return &response, nil
}

func (c *Client) do(ctx context.Context, method, apiPath string, body any, authenticated bool, out any) error {
	if ctx == nil {
		ctx = context.Background()
	}
	bodyBytes, err := encodeBody(body)
	if err != nil {
		return err
	}

	var lastErr *APIError
	for attempt := 0; attempt <= c.retries; attempt++ {
		if attempt > 0 {
			if err := waitForRetry(ctx, c.retryWait); err != nil {
				return err
			}
		}
		err := c.doOnce(ctx, method, apiPath, bodyBytes, authenticated, out)
		if err == nil {
			return nil
		}
		apiErr, ok := err.(*APIError)
		if !ok {
			return err
		}
		lastErr = apiErr
		if !apiErr.Retryable() || attempt == c.retries {
			return apiErr
		}
	}
	if lastErr != nil {
		return lastErr
	}
	return errors.New("specdriven: request failed")
}

func (c *Client) doOnce(ctx context.Context, method, apiPath string, body []byte, authenticated bool, out any) error {
	req, err := http.NewRequestWithContext(ctx, method, c.resolve(apiPath), bytes.NewReader(body))
	if err != nil {
		return fmt.Errorf("specdriven: create request: %w", err)
	}
	if body != nil {
		req.Header.Set("Content-Type", "application/json")
	}
	req.Header.Set("Accept", "application/json")
	if c.userAgent != "" {
		req.Header.Set("User-Agent", c.userAgent)
	}
	if authenticated {
		c.applyAuth(req)
	}

	resp, err := c.httpClient.Do(req)
	if err != nil {
		return newNetworkError(err)
	}
	defer resp.Body.Close()

	if resp.StatusCode < 200 || resp.StatusCode >= 300 {
		return decodeAPIError(resp)
	}
	if out == nil {
		_, _ = io.Copy(io.Discard, resp.Body)
		return nil
	}
	if err := json.NewDecoder(resp.Body).Decode(out); err != nil {
		return fmt.Errorf("specdriven: decode response: %w", err)
	}
	return nil
}

func (c *Client) resolve(apiPath string) string {
	base := *c.baseURL
	base.Path = strings.TrimRight(base.Path, "/") + "/api/v1" + apiPath
	base.RawQuery = ""
	if idx := strings.IndexByte(apiPath, '?'); idx >= 0 {
		base.Path = strings.TrimRight(c.baseURL.Path, "/") + "/api/v1" + apiPath[:idx]
		base.RawQuery = apiPath[idx+1:]
	}
	return base.String()
}

func (c *Client) applyAuth(req *http.Request) {
	switch c.authMode {
	case authBearer:
		req.Header.Set("Authorization", "Bearer "+c.authValue)
	case authAPIKey:
		req.Header.Set("X-API-Key", c.authValue)
	}
}

func encodeBody(body any) ([]byte, error) {
	if body == nil {
		return nil, nil
	}
	bodyBytes, err := json.Marshal(body)
	if err != nil {
		return nil, fmt.Errorf("specdriven: encode request: %w", err)
	}
	return bodyBytes, nil
}

func decodeAPIError(resp *http.Response) *APIError {
	bodyBytes, _ := io.ReadAll(resp.Body)
	if len(bodyBytes) == 0 {
		return newHTTPError(resp.StatusCode, "", "")
	}
	var payload errorResponse
	if err := json.Unmarshal(bodyBytes, &payload); err == nil {
		return newHTTPError(resp.StatusCode, payload.Error, payload.Message)
	}
	return newHTTPError(resp.StatusCode, "", strings.TrimSpace(string(bodyBytes)))
}
