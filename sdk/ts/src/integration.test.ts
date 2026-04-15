/**
 * Integration tests for the SpecDrivenClient.
 * These tests exercise full end-to-end flows against an in-process MSW mock server.
 * No live Java backend is required.
 */
import { http, HttpResponse } from "msw";
import { setupServer } from "msw/node";
import { afterAll, afterEach, beforeAll, beforeEach, describe, expect, it } from "vitest";
import { SpecDrivenClient } from "./client.js";
import { ApiError } from "./errors.js";

const BASE = "http://integration-test-backend";

const agentStore: Record<string, { state: string; createdAt: number; updatedAt: number }> =
  {};
const handledRequests: string[] = [];

function resetAgentStore() {
  for (const key of Object.keys(agentStore)) {
    delete agentStore[key];
  }
}

function recordRequest(request: Request) {
  handledRequests.push(`${request.method} ${new URL(request.url).pathname}`);
}

const server = setupServer(
  http.get(`${BASE}/api/v1/health`, ({ request }) => {
    recordRequest(request);
    return HttpResponse.json({ status: "ok", version: "0.1.0" });
  }),

  http.get(`${BASE}/api/v1/tools`, ({ request }) => {
    recordRequest(request);
    return HttpResponse.json({
      tools: [
        { name: "bash", description: "Run shell commands", parameters: [] },
        { name: "read", description: "Read a file", parameters: [] },
      ],
    });
  }),

  http.post(`${BASE}/api/v1/agent/run`, async ({ request }) => {
    recordRequest(request);
    const body = (await request.json()) as { prompt?: string };
    if (!body.prompt) {
      return HttpResponse.json(
        { status: 400, error: "invalid_params", message: "Missing prompt" },
        { status: 400 },
      );
    }
    const agentId = `agent-${Date.now()}`;
    agentStore[agentId] = {
      state: "STOPPED",
      createdAt: Date.now(),
      updatedAt: Date.now(),
    };
    return HttpResponse.json({ agentId, output: `processed: ${body.prompt}`, state: "STOPPED" });
  }),

  http.get(`${BASE}/api/v1/agent/state`, ({ request }) => {
    recordRequest(request);
    const url = new URL(request.url);
    const id = url.searchParams.get("id") ?? "";
    const entry = agentStore[id];
    if (!entry) {
      return HttpResponse.json(
        { status: 404, error: "not_found", message: "Agent not found" },
        { status: 404 },
      );
    }
    return HttpResponse.json({ agentId: id, ...entry });
  }),

  http.post(`${BASE}/api/v1/agent/stop`, ({ request }) => {
    recordRequest(request);
    const url = new URL(request.url);
    const id = url.searchParams.get("id") ?? "";
    const entry = agentStore[id];
    if (!entry) {
      return HttpResponse.json(
        { status: 404, error: "not_found", message: "Agent not found" },
        { status: 404 },
      );
    }
    entry.state = "STOPPED";
    entry.updatedAt = Date.now();
    return HttpResponse.json({});
  }),

  http.get(`${BASE}/api/v1/events`, ({ request }) => {
    recordRequest(request);
    const url = new URL(request.url);
    const after = parseInt(url.searchParams.get("after") ?? "0", 10);
    const events = [
      {
        sequence: 1,
        type: "AGENT_STATE_CHANGED",
        timestamp: 1000,
        source: "agent",
        metadata: {},
      },
      {
        sequence: 2,
        type: "TOOL_CALLED",
        timestamp: 2000,
        source: "agent",
        metadata: {},
      },
    ].filter((e) => e.sequence > after);
    const nextCursor = events.length > 0 ? events[events.length - 1].sequence : after;
    return HttpResponse.json({ events, nextCursor });
  }),
);

beforeAll(() => server.listen({ onUnhandledRequest: "error" }));
beforeEach(() => {
  resetAgentStore();
  handledRequests.length = 0;
});
afterEach(() => server.resetHandlers());
afterAll(() => server.close());

function makeClient() {
  return new SpecDrivenClient({
    baseUrl: BASE,
    auth: { bearerToken: "test-token" },
  });
}

describe("full workflow: health → listTools → runAgent → getAgentState → stopAgent → pollEvents", () => {
  it("completes successfully against mock backend", async () => {
    const client = makeClient();

    // health
    const health = await client.health();
    expect(health.status).toBe("ok");

    // listTools
    const tools = await client.listTools();
    expect(tools.tools.length).toBeGreaterThan(0);
    expect(tools.tools.some((t) => t.name === "bash")).toBe(true);

    // runAgent
    const run = await client.runAgent({ prompt: "summarize this" });
    expect(run.agentId).toBeTruthy();
    expect(run.state).toBe("STOPPED");
    expect(run.output).toContain("summarize this");

    // getAgentState
    const state = await client.getAgentState(run.agentId);
    expect(state.agentId).toBe(run.agentId);
    expect(state.state).toBe("STOPPED");

    // stopAgent
    await expect(client.stopAgent(run.agentId)).resolves.toBeUndefined();

    // pollEvents
    const events = await client.pollEvents();
    expect(events.events.length).toBeGreaterThan(0);
    expect(typeof events.nextCursor).toBe("number");
  });
});

describe("event cursor progression", () => {
  it("uses after parameter to advance cursor", async () => {
    const client = makeClient();

    const first = await client.pollEvents({ after: 0 });
    expect(first.events).toHaveLength(2);
    expect(first.nextCursor).toBe(2);

    const second = await client.pollEvents({ after: first.nextCursor });
    expect(second.events).toHaveLength(0);
  });
});

describe("error propagation", () => {
  it("returns ApiError with correct code on 400 response", async () => {
    const client = makeClient();
    const err = await client
      .runAgent({ prompt: "" })
      .catch((e: unknown) => e as ApiError);
    expect(err).toBeInstanceOf(ApiError);
    expect(err.status).toBe(400);
    expect(err.code).toBe("invalid_params");
    expect(err.retryable).toBe(false);
  });

  it("returns ApiError with 404 on unknown agent state", async () => {
    const client = makeClient();
    const err = await client
      .getAgentState("nonexistent-id")
      .catch((e: unknown) => e as ApiError);
    expect(err).toBeInstanceOf(ApiError);
    expect(err.status).toBe(404);
  });
});

describe("hermetic: no live backend required", () => {
  it("all flows complete without any external network calls", async () => {
    // The fake host plus `onUnhandledRequest: "error"` ensure the test suite
    // cannot fall through to a live backend or external network.
    const client = makeClient();
    await client.health();
    expect(handledRequests).toEqual(["GET /api/v1/health"]);
  });
});
