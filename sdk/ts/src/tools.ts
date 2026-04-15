import type { IncomingMessage, ServerResponse } from "node:http";

export interface RemoteToolRegistrationRequest {
  name: string;
  description: string;
  parameters: Array<Record<string, unknown>>;
  callbackUrl: string;
}

export interface RemoteToolInvocationRequest {
  toolName: string;
  parameters: Record<string, unknown>;
}

export interface RemoteToolInvocationResponse {
  success: boolean;
  output?: string;
  error?: string;
}

export interface ToolRegistrationResult {
  name: string;
  description: string;
  parameters: Array<Record<string, unknown>>;
}

export type ToolHandler = (parameters: Record<string, unknown>) => Promise<string>;

/** Dispatches Java backend tool callbacks to locally registered handlers. */
export class ToolCallbackHandler {
  private readonly handlers = new Map<string, ToolHandler>();

  register(name: string, handler: ToolHandler): void {
    const trimmedName = name?.trim();
    if (!trimmedName) {
      throw new Error("specdriven: tool name must not be empty");
    }
    if (typeof handler !== "function") {
      throw new Error("specdriven: tool handler must not be nil");
    }
    this.handlers.set(trimmedName, handler);
  }

  async handleRequest(req: IncomingMessage, res: ServerResponse): Promise<void> {
    if (req.method !== "POST") {
      this.writeJson(res, 405, { success: false, error: "method not allowed" });
      return;
    }

    let request: RemoteToolInvocationRequest;
    try {
      request = this.parseInvocationRequest(await this.readBody(req));
    } catch {
      this.writeJson(res, 400, {
        success: false,
        error: "invalid tool invocation request",
      });
      return;
    }

    const toolName = request.toolName.trim();
    if (!toolName) {
      this.writeJson(res, 200, { success: false, error: "toolName must not be empty" });
      return;
    }

    const handler = this.handlers.get(toolName);
    if (!handler) {
      this.writeJson(res, 200, { success: false, error: `tool not found: ${toolName}` });
      return;
    }

    try {
      const output = await handler(request.parameters);
      this.writeJson(res, 200, { success: true, output });
    } catch (error) {
      this.writeJson(res, 200, {
        success: false,
        error: error instanceof Error ? error.message : String(error),
      });
    }
  }

  private parseInvocationRequest(body: string): RemoteToolInvocationRequest {
    const parsed = JSON.parse(body) as Partial<RemoteToolInvocationRequest> | null;
    if (!parsed || typeof parsed !== "object" || Array.isArray(parsed)) {
      throw new Error("invalid request");
    }
    return {
      toolName: typeof parsed.toolName === "string" ? parsed.toolName : "",
      parameters: this.normalizeParameters(parsed.parameters),
    };
  }

  private normalizeParameters(parameters: unknown): Record<string, unknown> {
    if (!parameters || typeof parameters !== "object" || Array.isArray(parameters)) {
      return {};
    }
    return parameters as Record<string, unknown>;
  }

  private async readBody(req: IncomingMessage): Promise<string> {
    let body = "";
    for await (const chunk of req) {
      body += String(chunk);
    }
    return body;
  }

  private writeJson(
    res: ServerResponse,
    statusCode: number,
    payload: RemoteToolInvocationResponse,
  ): void {
    res.statusCode = statusCode;
    res.setHeader("Content-Type", "application/json");
    res.end(JSON.stringify(payload));
  }
}
