/** POST /api/v1/agent/run request body */
export interface RunAgentRequest {
  prompt: string;
  systemPrompt?: string;
  maxTurns?: number;
  toolTimeoutSeconds?: number;
}

/** POST /api/v1/agent/run response body */
export interface RunAgentResponse {
  agentId: string;
  output: string | null;
  state: string;
}

/** GET /api/v1/agent/state response body */
export interface AgentStateResponse {
  agentId: string;
  state: string;
  createdAt: number;
  updatedAt: number;
}

/** One tool entry returned by GET /api/v1/tools */
export interface ToolInfo {
  name: string;
  description: string;
  parameters: Array<Record<string, unknown>>;
}

/** GET /api/v1/tools response body */
export interface ToolsListResponse {
  tools: ToolInfo[];
}

/** GET /api/v1/health response body */
export interface HealthResponse {
  status: string;
  version: string;
}

/** Error response body returned on non-2xx responses */
export interface ErrorResponse {
  status: number;
  error: string;
  message: string;
  details?: Record<string, unknown> | null;
}

/** One event returned by GET /api/v1/events */
export interface Event {
  sequence: number;
  type: string;
  timestamp: number;
  source: string;
  metadata: Record<string, unknown>;
}

/** GET /api/v1/events response body */
export interface EventPollResponse {
  events: Event[];
  nextCursor: number;
}

/** Options for a single event polling request */
export interface PollEventsOptions {
  /** Return only events with sequence > after */
  after?: number;
  /** Maximum number of events to return */
  limit?: number;
  /** Filter by event type */
  type?: string;
}
