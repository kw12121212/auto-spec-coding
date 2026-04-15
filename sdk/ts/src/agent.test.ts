import { describe, expect, it, vi } from "vitest";
import { SpecDrivenAgent } from "./agent.js";
import { SpecDrivenClient } from "./client.js";
import type { AgentStateResponse, RunAgentRequest, RunAgentResponse } from "./models.js";

function makeClientMock(runResponse?: Partial<RunAgentResponse>) {
  const defaultRun: RunAgentResponse = {
    agentId: "agent-1",
    output: "hello",
    state: "STOPPED",
    ...runResponse,
  };
  const stateResponse: AgentStateResponse = {
    agentId: "agent-1",
    state: "STOPPED",
    createdAt: 1000,
    updatedAt: 2000,
  };
  return {
    runAgent: vi.fn((_req: RunAgentRequest) => Promise.resolve(defaultRun)),
    stopAgent: vi.fn((_id: string) => Promise.resolve()),
    getAgentState: vi.fn((_id: string) => Promise.resolve(stateResponse)),
  };
}

describe("SpecDrivenAgent.run()", () => {
  it("delegates the prompt to client.runAgent and returns AgentRunResult", async () => {
    const client = makeClientMock();
    const agent = new SpecDrivenAgent(client as never);

    const result = await agent.run("do something");

    expect(client.runAgent).toHaveBeenCalledOnce();
    expect(client.runAgent).toHaveBeenCalledWith(
      expect.objectContaining({ prompt: "do something" }),
    );
    expect(result).toEqual({ agentId: "agent-1", output: "hello", state: "STOPPED" });
  });

  it("forwards AgentConfig defaults when no per-call options are given", async () => {
    const client = makeClientMock();
    const agent = new SpecDrivenAgent(client as never, {
      systemPrompt: "You are helpful",
      maxTurns: 5,
      toolTimeoutSeconds: 30,
    });

    await agent.run("hello");

    expect(client.runAgent).toHaveBeenCalledWith({
      prompt: "hello",
      systemPrompt: "You are helpful",
      maxTurns: 5,
      toolTimeoutSeconds: 30,
    });
  });

  it("per-call options override AgentConfig defaults", async () => {
    const client = makeClientMock();
    const agent = new SpecDrivenAgent(client as never, {
      systemPrompt: "Default prompt",
      maxTurns: 5,
      toolTimeoutSeconds: 30,
    });

    await agent.run("hello", { systemPrompt: "Override prompt", maxTurns: 10 });

    expect(client.runAgent).toHaveBeenCalledWith({
      prompt: "hello",
      systemPrompt: "Override prompt",
      maxTurns: 10,
      toolTimeoutSeconds: 30,
    });
  });

  it("returns null output when backend returns null", async () => {
    const client = makeClientMock({ output: null });
    const agent = new SpecDrivenAgent(client as never);

    const result = await agent.run("test");
    expect(result.output).toBeNull();
  });
});

describe("SpecDrivenAgent.stop()", () => {
  it("delegates agent ID to client.stopAgent", async () => {
    const client = makeClientMock();
    const agent = new SpecDrivenAgent(client as never);

    await agent.stop("agent-abc");

    expect(client.stopAgent).toHaveBeenCalledOnce();
    expect(client.stopAgent).toHaveBeenCalledWith("agent-abc");
  });

  it("returns void", async () => {
    const client = makeClientMock();
    const agent = new SpecDrivenAgent(client as never);

    const result = await agent.stop("agent-abc");
    expect(result).toBeUndefined();
  });
});

describe("SpecDrivenAgent.getState()", () => {
  it("delegates agent ID to client.getAgentState and returns response", async () => {
    const client = makeClientMock();
    const agent = new SpecDrivenAgent(client as never);

    const result = await agent.getState("agent-1");

    expect(client.getAgentState).toHaveBeenCalledOnce();
    expect(client.getAgentState).toHaveBeenCalledWith("agent-1");
    expect(result).toEqual({
      agentId: "agent-1",
      state: "STOPPED",
      createdAt: 1000,
      updatedAt: 2000,
    });
  });
});

describe("client.agent() factory", () => {
  it("returns a SpecDrivenAgent bound to the client", () => {
    const client = new SpecDrivenClient({ baseUrl: "http://test-backend" });
    const agent = client.agent();
    expect(agent).toBeInstanceOf(SpecDrivenAgent);
  });

  it("returns a SpecDrivenAgent bound to the client with config", () => {
    const client = new SpecDrivenClient({ baseUrl: "http://test-backend" });
    const agent = client.agent({ maxTurns: 5, systemPrompt: "test" });
    expect(agent).toBeInstanceOf(SpecDrivenAgent);
  });
});
