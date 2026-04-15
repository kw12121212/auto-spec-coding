import { http, HttpResponse } from "msw";
import { setupServer } from "msw/node";
import { afterAll, afterEach, beforeAll, describe, expect, it } from "vitest";
import { SpecDrivenClient } from "./client.js";
import { ApiError } from "./errors.js";

const BASE = "http://test-backend";

const server = setupServer();
beforeAll(() => server.listen({ onUnhandledRequest: "error" }));
afterEach(() => server.resetHandlers());
afterAll(() => server.close());

function makeClient(opts?: Partial<ConstructorParameters<typeof SpecDrivenClient>[0]>) {
  return new SpecDrivenClient({ baseUrl: BASE, ...opts });
}

describe("SpecDrivenClient constructor", () => {
  it("throws on empty base URL", () => {
    expect(() => new SpecDrivenClient({ baseUrl: "" })).toThrow(
      "specdriven: base URL must not be empty",
    );
  });

  it("throws on whitespace-only base URL", () => {
    expect(() => new SpecDrivenClient({ baseUrl: "   " })).toThrow();
  });

  it("throws on URL without scheme", () => {
    expect(() => new SpecDrivenClient({ baseUrl: "localhost:8080" })).toThrow();
  });

  it("constructs successfully with valid URL", () => {
    expect(() => makeClient()).not.toThrow();
  });
});

describe("health()", () => {
  it("calls GET /api/v1/health and returns decoded response", async () => {
    let capturedUrl = "";
    let capturedMethod = "";
    let capturedAuth = "";
    server.use(
      http.get(`${BASE}/api/v1/health`, ({ request }) => {
        capturedUrl = request.url;
        capturedMethod = request.method;
        capturedAuth = request.headers.get("Authorization") ?? "";
        return HttpResponse.json({ status: "ok", version: "0.1.0" });
      }),
    );

    const client = makeClient();
    const res = await client.health();

    expect(capturedMethod).toBe("GET");
    expect(capturedUrl).toBe(`${BASE}/api/v1/health`);
    expect(capturedAuth).toBe("");
    expect(res.status).toBe("ok");
    expect(res.version).toBe("0.1.0");
  });

  it("does not send auth header even when auth is configured", async () => {
    let capturedAuth: string | null = null;
    server.use(
      http.get(`${BASE}/api/v1/health`, ({ request }) => {
        capturedAuth = request.headers.get("Authorization");
        return HttpResponse.json({ status: "ok", version: "0.1.0" });
      }),
    );

    const client = makeClient({ auth: { bearerToken: "my-token" } });
    await client.health();
    expect(capturedAuth).toBeNull();
  });
});

describe("listTools()", () => {
  it("calls GET /api/v1/tools and returns decoded response", async () => {
    server.use(
      http.get(`${BASE}/api/v1/tools`, () =>
        HttpResponse.json({
          tools: [{ name: "bash", description: "shell", parameters: [] }],
        }),
      ),
    );

    const res = await makeClient().listTools();
    expect(res.tools).toHaveLength(1);
    expect(res.tools[0].name).toBe("bash");
  });

  it("normalizes null tools to empty array", async () => {
    server.use(
      http.get(`${BASE}/api/v1/tools`, () => HttpResponse.json({ tools: null })),
    );
    const res = await makeClient().listTools();
    expect(res.tools).toEqual([]);
  });
});

describe("runAgent()", () => {
  it("calls POST /api/v1/agent/run with correct body and returns response", async () => {
    let capturedBody: unknown;
    let capturedMethod = "";
    server.use(
      http.post(`${BASE}/api/v1/agent/run`, async ({ request }) => {
        capturedBody = await request.json();
        capturedMethod = request.method;
        return HttpResponse.json({
          agentId: "agent-1",
          output: "hello",
          state: "STOPPED",
        });
      }),
    );

    const res = await makeClient().runAgent({ prompt: "hello" });
    expect(capturedMethod).toBe("POST");
    expect((capturedBody as { prompt: string }).prompt).toBe("hello");
    expect(res.agentId).toBe("agent-1");
    expect(res.output).toBe("hello");
    expect(res.state).toBe("STOPPED");
  });
});

describe("stopAgent()", () => {
  it("calls POST /api/v1/agent/stop?id=<agentId>", async () => {
    let capturedUrl = "";
    server.use(
      http.post(`${BASE}/api/v1/agent/stop`, ({ request }) => {
        capturedUrl = request.url;
        return HttpResponse.json({});
      }),
    );

    await makeClient().stopAgent("agent-abc");
    expect(capturedUrl).toBe(`${BASE}/api/v1/agent/stop?id=agent-abc`);
  });

  it("throws on empty agent ID", async () => {
    await expect(makeClient().stopAgent("")).rejects.toThrow("agent ID must not be empty");
  });
});

describe("getAgentState()", () => {
  it("calls GET /api/v1/agent/state?id=<agentId> and returns state", async () => {
    let capturedUrl = "";
    server.use(
      http.get(`${BASE}/api/v1/agent/state`, ({ request }) => {
        capturedUrl = request.url;
        return HttpResponse.json({
          agentId: "agent-1",
          state: "STOPPED",
          createdAt: 1000,
          updatedAt: 2000,
        });
      }),
    );

    const res = await makeClient().getAgentState("agent-1");
    expect(capturedUrl).toBe(`${BASE}/api/v1/agent/state?id=agent-1`);
    expect(res.agentId).toBe("agent-1");
    expect(res.state).toBe("STOPPED");
    expect(res.createdAt).toBe(1000);
    expect(res.updatedAt).toBe(2000);
  });

  it("throws on empty agent ID", async () => {
    await expect(makeClient().getAgentState("")).rejects.toThrow(
      "agent ID must not be empty",
    );
  });
});

describe("pollEvents()", () => {
  it("calls GET /api/v1/events and returns decoded events", async () => {
    let capturedUrl = "";
    server.use(
      http.get(`${BASE}/api/v1/events`, ({ request }) => {
        capturedUrl = request.url;
        return HttpResponse.json({
          events: [
            {
              sequence: 1,
              type: "AGENT_STATE_CHANGED",
              timestamp: 100,
              source: "agent",
              metadata: {},
            },
          ],
          nextCursor: 1,
        });
      }),
    );

    const res = await makeClient().pollEvents();
    expect(capturedUrl).toBe(`${BASE}/api/v1/events`);
    expect(res.events).toHaveLength(1);
    expect(res.nextCursor).toBe(1);
  });

  it("sends after, limit, type query parameters", async () => {
    let capturedUrl = "";
    server.use(
      http.get(`${BASE}/api/v1/events`, ({ request }) => {
        capturedUrl = request.url;
        return HttpResponse.json({ events: [], nextCursor: 5 });
      }),
    );

    await makeClient().pollEvents({ after: 5, limit: 10, type: "AGENT_STATE_CHANGED" });
    const url = new URL(capturedUrl);
    expect(url.searchParams.get("after")).toBe("5");
    expect(url.searchParams.get("limit")).toBe("10");
    expect(url.searchParams.get("type")).toBe("AGENT_STATE_CHANGED");
  });

  it("normalizes null events to empty array", async () => {
    server.use(
      http.get(`${BASE}/api/v1/events`, () =>
        HttpResponse.json({ events: null, nextCursor: 0 }),
      ),
    );
    const res = await makeClient().pollEvents();
    expect(res.events).toEqual([]);
  });
});

describe("authentication headers", () => {
  it("sends Authorization: Bearer <token> when configured with bearerToken", async () => {
    let capturedAuthHeader: string | null = null;
    server.use(
      http.get(`${BASE}/api/v1/tools`, ({ request }) => {
        capturedAuthHeader = request.headers.get("Authorization");
        return HttpResponse.json({ tools: [] });
      }),
    );
    const client = makeClient({ auth: { bearerToken: "my-secret" } });
    await client.listTools();
    expect(capturedAuthHeader).toBe("Bearer my-secret");
  });

  it("sends X-API-Key header when configured with apiKey", async () => {
    let capturedApiKeyHeader: string | null = null;
    server.use(
      http.get(`${BASE}/api/v1/tools`, ({ request }) => {
        capturedApiKeyHeader = request.headers.get("X-API-Key");
        return HttpResponse.json({ tools: [] });
      }),
    );
    const client = makeClient({ auth: { apiKey: "my-api-key" } });
    await client.listTools();
    expect(capturedApiKeyHeader).toBe("my-api-key");
  });
});

describe("error handling", () => {
  it("throws ApiError on non-2xx response with JSON body", async () => {
    server.use(
      http.post(`${BASE}/api/v1/agent/run`, () =>
        HttpResponse.json(
          { status: 400, error: "invalid_params", message: "Missing prompt" },
          { status: 400 },
        ),
      ),
    );

    await expect(makeClient().runAgent({ prompt: "x" })).rejects.toMatchObject({
      status: 400,
      code: "invalid_params",
      retryable: false,
    });
  });

  it("throws ApiError on non-2xx response with non-JSON body", async () => {
    server.use(
      http.post(`${BASE}/api/v1/agent/run`, () =>
        new HttpResponse("Internal Server Error", { status: 500 }),
      ),
    );

    const err = await makeClient()
      .runAgent({ prompt: "x" })
      .catch((e: unknown) => e as ApiError);
    expect(err).toBeInstanceOf(ApiError);
    expect(err.status).toBe(500);
  });

  it("throws ApiError (network_error) on network failure", async () => {
    server.use(
      http.post(`${BASE}/api/v1/agent/run`, () => HttpResponse.error()),
    );

    const err = await makeClient()
      .runAgent({ prompt: "x" })
      .catch((e: unknown) => e as ApiError);
    expect(err).toBeInstanceOf(ApiError);
    expect(err.code).toBe("network_error");
    expect(err.retryable).toBe(true);
  });
});
