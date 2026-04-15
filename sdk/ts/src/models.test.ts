import { describe, expect, it } from "vitest";
import type {
  AgentStateResponse,
  EventPollResponse,
  HealthResponse,
  RunAgentRequest,
  RunAgentResponse,
  ToolInfo,
  ToolsListResponse,
} from "./models.js";

describe("RunAgentRequest JSON field names", () => {
  it("serializes prompt field as 'prompt'", () => {
    const req: RunAgentRequest = { prompt: "hello" };
    const json = JSON.parse(JSON.stringify(req));
    expect(json.prompt).toBe("hello");
    expect(json.systemPrompt).toBeUndefined();
    expect(json.maxTurns).toBeUndefined();
    expect(json.toolTimeoutSeconds).toBeUndefined();
  });

  it("serializes optional fields using Java API field names", () => {
    const req: RunAgentRequest = {
      prompt: "review",
      systemPrompt: "You are a reviewer",
      maxTurns: 10,
      toolTimeoutSeconds: 30,
    };
    const json = JSON.parse(JSON.stringify(req));
    expect(json.prompt).toBe("review");
    expect(json.systemPrompt).toBe("You are a reviewer");
    expect(json.maxTurns).toBe(10);
    expect(json.toolTimeoutSeconds).toBe(30);
  });
});

describe("RunAgentResponse JSON field names", () => {
  it("deserializes using Java API field names", () => {
    const raw = { agentId: "abc-123", output: "done", state: "STOPPED" };
    const res: RunAgentResponse = raw;
    expect(res.agentId).toBe("abc-123");
    expect(res.output).toBe("done");
    expect(res.state).toBe("STOPPED");
  });

  it("supports null output", () => {
    const res: RunAgentResponse = { agentId: "x", output: null, state: "RUNNING" };
    expect(res.output).toBeNull();
  });
});

describe("AgentStateResponse JSON field names", () => {
  it("deserializes agentId, state, createdAt, updatedAt", () => {
    const raw = {
      agentId: "abc-123",
      state: "RUNNING",
      createdAt: 1000,
      updatedAt: 2000,
    };
    const res: AgentStateResponse = raw;
    expect(res.agentId).toBe("abc-123");
    expect(res.state).toBe("RUNNING");
    expect(res.createdAt).toBe(1000);
    expect(res.updatedAt).toBe(2000);
  });
});

describe("ToolInfo JSON field names", () => {
  it("deserializes name, description, parameters", () => {
    const raw = {
      name: "bash",
      description: "Execute shell commands",
      parameters: [{ name: "command", type: "string" }],
    };
    const tool: ToolInfo = raw;
    expect(tool.name).toBe("bash");
    expect(tool.description).toBe("Execute shell commands");
    expect(tool.parameters).toHaveLength(1);
  });
});

describe("ToolsListResponse JSON field names", () => {
  it("deserializes tools array", () => {
    const raw = { tools: [{ name: "bash", description: "shell", parameters: [] }] };
    const res: ToolsListResponse = raw;
    expect(res.tools).toHaveLength(1);
    expect(res.tools[0].name).toBe("bash");
  });
});

describe("HealthResponse JSON field names", () => {
  it("deserializes status and version", () => {
    const res: HealthResponse = { status: "ok", version: "0.1.0" };
    expect(res.status).toBe("ok");
    expect(res.version).toBe("0.1.0");
  });
});

describe("EventPollResponse JSON field names", () => {
  it("deserializes events and nextCursor", () => {
    const raw: EventPollResponse = {
      events: [
        {
          sequence: 1,
          type: "AGENT_STATE_CHANGED",
          timestamp: 999,
          source: "agent",
          metadata: {},
        },
      ],
      nextCursor: 1,
    };
    expect(raw.events).toHaveLength(1);
    expect(raw.events[0].sequence).toBe(1);
    expect(raw.nextCursor).toBe(1);
  });

  it("supports empty events array", () => {
    const raw: EventPollResponse = { events: [], nextCursor: 0 };
    expect(raw.events).toHaveLength(0);
  });
});
