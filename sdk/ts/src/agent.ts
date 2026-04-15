import type { AgentStateResponse } from "./models.js";
import type { SpecDrivenClient } from "./client.js";

/** Configuration defaults applied to every `agent.run()` call. */
export interface AgentConfig {
  systemPrompt?: string;
  maxTurns?: number;
  toolTimeoutSeconds?: number;
}

/** Result returned from a completed `agent.run()` call. */
export type AgentRunResult = {
  agentId: string;
  output: string | null;
  state: string;
};

/** High-level agent lifecycle wrapper around a `SpecDrivenClient`. */
export class SpecDrivenAgent {
  private readonly client: SpecDrivenClient;
  private readonly config: AgentConfig;

  constructor(client: SpecDrivenClient, config: AgentConfig = {}) {
    this.client = client;
    this.config = config;
  }

  /**
   * Run a prompt to completion and return the final result.
   * Per-call options override `AgentConfig` defaults.
   */
  async run(prompt: string, options: AgentConfig = {}): Promise<AgentRunResult> {
    const res = await this.client.runAgent({
      prompt,
      systemPrompt: options.systemPrompt ?? this.config.systemPrompt,
      maxTurns: options.maxTurns ?? this.config.maxTurns,
      toolTimeoutSeconds: options.toolTimeoutSeconds ?? this.config.toolTimeoutSeconds,
    });
    return { agentId: res.agentId, output: res.output, state: res.state };
  }

  /** Stop a running agent by ID. */
  async stop(agentId: string): Promise<void> {
    await this.client.stopAgent(agentId);
  }

  /** Return the current state of an agent by ID. */
  async getState(agentId: string): Promise<AgentStateResponse> {
    return this.client.getAgentState(agentId);
  }
}
