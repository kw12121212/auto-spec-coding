import { createServer } from "node:http";
import type { AddressInfo } from "node:net";
import { afterEach, describe, expect, it, vi } from "vitest";
import { SpecDrivenClient } from "./client.js";
import { ToolCallbackHandler } from "./tools.js";
import type {
  RemoteToolInvocationResponse,
  RemoteToolRegistrationRequest,
  ToolHandler,
} from "./tools.js";

type CapturedRequest = {
  url: string;
  method: string;
  headers: Headers;
  body?: unknown;
};

const callbackServers = new Set<ReturnType<typeof createServer>>();

afterEach(async () => {
  await Promise.all(
    Array.from(callbackServers, (server) =>
      new Promise<void>((resolve, reject) => {
        server.close((error) => {
          if (error) {
            reject(error);
            return;
          }
          resolve();
        });
      }),
    ),
  );
  callbackServers.clear();
});

async function startCallbackServer(handler: ToolCallbackHandler): Promise<string> {
  const server = createServer((req, res) => {
    void handler.handleRequest(req, res);
  });
  callbackServers.add(server);
  await new Promise<void>((resolve, reject) => {
    server.listen(0, "127.0.0.1", () => resolve());
    server.once("error", reject);
  });
  const address = server.address() as AddressInfo;
  return `http://127.0.0.1:${address.port}`;
}

async function invokeTool(
  baseUrl: string,
  method: string,
  body?: string,
): Promise<{ status: number; payload: RemoteToolInvocationResponse }> {
  const response = await fetch(baseUrl, {
    method,
    headers: body ? { "Content-Type": "application/json" } : undefined,
    body,
  });
  return {
    status: response.status,
    payload: (await response.json()) as RemoteToolInvocationResponse,
  };
}

function makeClient(
  captured: CapturedRequest[],
  responseBody: unknown = { name: "lookup", description: "lookup data", parameters: [] },
) {
  return new SpecDrivenClient({
    baseUrl: "http://test-backend",
    auth: { bearerToken: "token-1" },
    maxRetries: 2,
    fetch: vi.fn(async (input: URL | RequestInfo, init?: RequestInit) => {
      const request = new Request(input, init);
      captured.push({
        url: request.url,
        method: request.method,
        headers: request.headers,
        body: init?.body ? JSON.parse(String(init.body)) : undefined,
      });
      return new Response(JSON.stringify(responseBody), {
        status: 200,
        headers: { "Content-Type": "application/json" },
      });
    }),
  });
}

describe("ToolCallbackHandler.register", () => {
  it("rejects empty name", () => {
    const handler = new ToolCallbackHandler();
    expect(() => handler.register("", async () => "ok")).toThrow(
      "specdriven: tool name must not be empty",
    );
  });

  it("rejects null handler", () => {
    const handler = new ToolCallbackHandler();
    expect(() => handler.register("lookup", null as unknown as ToolHandler)).toThrow(
      "specdriven: tool handler must not be nil",
    );
  });

  it("stores valid handler", async () => {
    const handler = new ToolCallbackHandler();
    handler.register("lookup", async (parameters) => String(parameters.q));
    const baseUrl = await startCallbackServer(handler);

    const response = await invokeTool(
      baseUrl,
      "POST",
      JSON.stringify({ toolName: "lookup", parameters: { q: "stored" } }),
    );

    expect(response.status).toBe(200);
    expect(response.payload).toEqual({ success: true, output: "stored" });
  });

  it("replaces existing handler for the same name", async () => {
    const handler = new ToolCallbackHandler();
    handler.register("lookup", async () => "first");
    handler.register("lookup", async () => "second");
    const baseUrl = await startCallbackServer(handler);

    const response = await invokeTool(
      baseUrl,
      "POST",
      JSON.stringify({ toolName: "lookup", parameters: {} }),
    );

    expect(response.status).toBe(200);
    expect(response.payload).toEqual({ success: true, output: "second" });
  });
});

describe("ToolCallbackHandler.handleRequest", () => {
  it("dispatches known tool", async () => {
    const handler = new ToolCallbackHandler();
    const fn = vi.fn(async (parameters: Record<string, unknown>) => `value:${parameters.q}`);
    handler.register("lookup", fn);
    const baseUrl = await startCallbackServer(handler);

    const response = await invokeTool(
      baseUrl,
      "POST",
      JSON.stringify({ toolName: "lookup", parameters: { q: "x" } }),
    );

    expect(fn).toHaveBeenCalledWith({ q: "x" });
    expect(response.status).toBe(200);
    expect(response.payload).toEqual({ success: true, output: "value:x" });
  });

  it("returns error for unknown tool", async () => {
    const baseUrl = await startCallbackServer(new ToolCallbackHandler());

    const response = await invokeTool(
      baseUrl,
      "POST",
      JSON.stringify({ toolName: "missing", parameters: {} }),
    );

    expect(response.status).toBe(200);
    expect(response.payload).toEqual({ success: false, error: "tool not found: missing" });
  });

  it("returns 405 for non-POST", async () => {
    const baseUrl = await startCallbackServer(new ToolCallbackHandler());

    const response = await invokeTool(baseUrl, "GET");

    expect(response.status).toBe(405);
    expect(response.payload).toEqual({ success: false, error: "method not allowed" });
  });

  it("returns 400 for invalid JSON", async () => {
    const baseUrl = await startCallbackServer(new ToolCallbackHandler());

    const response = await invokeTool(baseUrl, "POST", "{broken");

    expect(response.status).toBe(400);
    expect(response.payload).toEqual({
      success: false,
      error: "invalid tool invocation request",
    });
  });

  it("propagates handler error as success false", async () => {
    const handler = new ToolCallbackHandler();
    handler.register("lookup", async () => {
      throw new Error("not found");
    });
    const baseUrl = await startCallbackServer(handler);

    const response = await invokeTool(
      baseUrl,
      "POST",
      JSON.stringify({ toolName: "lookup", parameters: {} }),
    );

    expect(response.status).toBe(200);
    expect(response.payload).toEqual({ success: false, error: "not found" });
  });
});

describe("SpecDrivenClient.registerRemoteTool", () => {
  it("sends POST /api/v1/tools/register with correct payload", async () => {
    const requests: CapturedRequest[] = [];
    const client = makeClient(requests);
    const request: RemoteToolRegistrationRequest = {
      name: "lookup",
      description: "lookup data",
      callbackUrl: "http://localhost/callback",
      parameters: [{ name: "term", type: "string" }],
    };

    const response = await client.registerRemoteTool(request);

    expect(requests).toHaveLength(1);
    expect(requests[0].url).toBe("http://test-backend/api/v1/tools/register");
    expect(requests[0].method).toBe("POST");
    expect(requests[0].headers.get("authorization")).toBe("Bearer token-1");
    expect(requests[0].body).toEqual(request);
    expect(response).toEqual({
      name: "lookup",
      description: "lookup data",
      parameters: [],
    });
  });

  it("rejects empty name", async () => {
    const requests: CapturedRequest[] = [];
    const client = makeClient(requests);

    await expect(
      client.registerRemoteTool({
        name: " ",
        description: "lookup data",
        callbackUrl: "http://localhost/callback",
        parameters: [],
      }),
    ).rejects.toThrow("specdriven: tool name must not be empty");
    expect(requests).toEqual([]);
  });

  it("rejects empty callbackUrl", async () => {
    const requests: CapturedRequest[] = [];
    const client = makeClient(requests);

    await expect(
      client.registerRemoteTool({
        name: "lookup",
        description: "lookup data",
        callbackUrl: " ",
        parameters: [],
      }),
    ).rejects.toThrow("specdriven: tool callback URL must not be empty");
    expect(requests).toEqual([]);
  });
});
