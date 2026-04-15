import { ApiError } from "./errors.js";
import { withRetry } from "./retry.js";
import type {
  AgentStateResponse,
  ErrorResponse,
  EventPollResponse,
  HealthResponse,
  PollEventsOptions,
  RunAgentRequest,
  RunAgentResponse,
  ToolsListResponse,
} from "./models.js";
import { SpecDrivenAgent } from "./agent.js";
import type { AgentConfig } from "./agent.js";
import type { RemoteToolRegistrationRequest, ToolRegistrationResult } from "./tools.js";

const DEFAULT_USER_AGENT = "specdriven-ts-sdk/0.1.0";

type FetchFn = typeof globalThis.fetch;

export interface ClientConfig {
  /** Base URL of the SpecDriven Java backend, e.g. "http://localhost:8080" */
  baseUrl: string;
  /** Authentication credentials */
  auth?: { bearerToken: string } | { apiKey: string };
  /** Custom fetch implementation (defaults to globalThis.fetch) */
  fetch?: FetchFn;
  /** Number of retry attempts for retryable failures (default: 0, no retries) */
  maxRetries?: number;
}

/** Low-level HTTP client for the SpecDriven Java backend REST API. */
export class SpecDrivenClient {
  private readonly baseUrl: string;
  private readonly auth: ClientConfig["auth"];
  private readonly fetchFn: FetchFn;
  private readonly maxRetries: number;

  constructor(config: ClientConfig) {
    const trimmed = config.baseUrl?.trim();
    if (!trimmed) {
      throw new Error("specdriven: base URL must not be empty");
    }
    let parsed: URL;
    try {
      parsed = new URL(trimmed);
    } catch {
      throw new Error(`specdriven: invalid base URL: ${trimmed}`);
    }
    if (!parsed.protocol || !parsed.host) {
      throw new Error("specdriven: base URL must include scheme and host");
    }
    this.baseUrl = trimmed.replace(/\/+$/, "");
    this.auth = config.auth;
    this.fetchFn = config.fetch ?? globalThis.fetch;
    this.maxRetries = config.maxRetries ?? 0;
  }

  /** GET /api/v1/health — does not require authentication */
  async health(): Promise<HealthResponse> {
    return this.request<HealthResponse>("GET", "/health", undefined, false);
  }

  /** GET /api/v1/tools */
  async listTools(): Promise<ToolsListResponse> {
    const res = await this.request<ToolsListResponse>("GET", "/tools");
    return { tools: res.tools ?? [] };
  }

  /** POST /api/v1/tools/register */
  async registerRemoteTool(
    request: RemoteToolRegistrationRequest,
  ): Promise<ToolRegistrationResult> {
    const name = request.name?.trim();
    if (!name) {
      throw new Error("specdriven: tool name must not be empty");
    }
    const callbackUrl = request.callbackUrl?.trim();
    if (!callbackUrl) {
      throw new Error("specdriven: tool callback URL must not be empty");
    }
    const res = await this.request<ToolRegistrationResult>("POST", "/tools/register", request);
    return { ...res, parameters: res.parameters ?? [] };
  }

  /** POST /api/v1/agent/run */
  async runAgent(request: RunAgentRequest): Promise<RunAgentResponse> {
    return this.request<RunAgentResponse>("POST", "/agent/run", request);
  }

  /** POST /api/v1/agent/stop?id=<agentId> */
  async stopAgent(agentId: string): Promise<void> {
    const id = agentId?.trim();
    if (!id) {
      throw new Error("specdriven: agent ID must not be empty");
    }
    await this.request<void>(
      "POST",
      `/agent/stop?id=${encodeURIComponent(id)}`,
      undefined,
      true,
      true,
    );
  }

  /** GET /api/v1/agent/state?id=<agentId> */
  async getAgentState(agentId: string): Promise<AgentStateResponse> {
    const id = agentId?.trim();
    if (!id) {
      throw new Error("specdriven: agent ID must not be empty");
    }
    return this.request<AgentStateResponse>(
      "GET",
      `/agent/state?id=${encodeURIComponent(id)}`,
    );
  }

  /** Return a `SpecDrivenAgent` bound to this client with optional default config. */
  agent(config?: AgentConfig): SpecDrivenAgent {
    return new SpecDrivenAgent(this, config);
  }

  /** GET /api/v1/events */
  async pollEvents(options: PollEventsOptions = {}): Promise<EventPollResponse> {
    const params = new URLSearchParams();
    if (options.after != null && options.after > 0) {
      params.set("after", String(options.after));
    }
    if (options.limit != null && options.limit > 0) {
      params.set("limit", String(options.limit));
    }
    if (options.type?.trim()) {
      params.set("type", options.type.trim());
    }
    const query = params.toString();
    const path = query ? `/events?${query}` : "/events";
    const res = await this.request<EventPollResponse>("GET", path);
    return { events: res.events ?? [], nextCursor: res.nextCursor ?? 0 };
  }

  private async request<T>(
    method: string,
    apiPath: string,
    body?: unknown,
    authenticated = true,
    noBody = false,
  ): Promise<T> {
    return withRetry(
      () => this.doOnce<T>(method, apiPath, body, authenticated, noBody),
      this.maxRetries,
    );
  }

  private async doOnce<T>(
    method: string,
    apiPath: string,
    body: unknown,
    authenticated: boolean,
    noBody: boolean,
  ): Promise<T> {
    const url = this.resolve(apiPath);
    const headers: Record<string, string> = {
      Accept: "application/json",
      "User-Agent": DEFAULT_USER_AGENT,
    };

    let bodyStr: string | undefined;
    if (body !== undefined) {
      bodyStr = JSON.stringify(body);
      headers["Content-Type"] = "application/json";
    }

    if (authenticated) {
      this.applyAuth(headers);
    }

    let response: Response;
    try {
      response = await this.fetchFn(url, {
        method,
        headers,
        body: bodyStr,
      });
    } catch (err) {
      throw ApiError.fromNetworkError(err);
    }

    if (!response.ok) {
      throw await decodeApiError(response);
    }

    if (noBody) {
      return undefined as T;
    }
    const json: T = (await response.json()) as T;
    return json;
  }

  private resolve(apiPath: string): string {
    const qIdx = apiPath.indexOf("?");
    if (qIdx >= 0) {
      const path = apiPath.slice(0, qIdx);
      const query = apiPath.slice(qIdx);
      return `${this.baseUrl}/api/v1${path}${query}`;
    }
    return `${this.baseUrl}/api/v1${apiPath}`;
  }

  private applyAuth(headers: Record<string, string>): void {
    if (!this.auth) return;
    if ("bearerToken" in this.auth) {
      headers["Authorization"] = `Bearer ${this.auth.bearerToken}`;
    } else if ("apiKey" in this.auth) {
      headers["X-API-Key"] = this.auth.apiKey;
    }
  }
}

async function decodeApiError(response: Response): Promise<ApiError> {
  let text = "";
  try {
    text = await response.text();
  } catch {
    // ignore body read failure
  }
  if (text) {
    try {
      const payload = JSON.parse(text) as Partial<ErrorResponse>;
      return ApiError.fromHttpStatus(
        response.status,
        payload.error ?? "",
        payload.message ?? "",
      );
    } catch {
      // fall through to plain text message
    }
  }
  return ApiError.fromHttpStatus(response.status, "", text.trim());
}
