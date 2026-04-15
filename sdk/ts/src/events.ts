import type { Event, PollEventsOptions } from "./models.js";

const DEFAULT_POLL_INTERVAL_MS = 1000;

export interface EventSubscriptionOptions extends PollEventsOptions {
  /** Delay between completed polling cycles in milliseconds. */
  pollIntervalMs?: number;
}

export interface EventPollingClient {
  pollEvents(options?: PollEventsOptions): Promise<{
    events?: Event[] | null;
    nextCursor?: number | null;
  }>;
}

export interface EventSubscription extends AsyncIterable<Event>, AsyncIterator<Event> {
  readonly stopped: boolean;
  stop(): void;
  close(): Promise<void>;
}

export function createEventSubscription(
  client: EventPollingClient,
  options: EventSubscriptionOptions = {},
): EventSubscription {
  return new PollingEventSubscription(client, options);
}

class PollingEventSubscription implements EventSubscription {
  private readonly client: EventPollingClient;
  private readonly limit: number | undefined;
  private readonly type: string | undefined;
  private readonly pollIntervalMs: number;
  private readonly buffer: Event[] = [];
  private currentCursor: number | undefined;
  private stoppedValue = false;
  private nextPollAt = 0;
  private sleepHandle: ReturnType<typeof setTimeout> | null = null;
  private sleepResolve: (() => void) | null = null;

  constructor(client: EventPollingClient, options: EventSubscriptionOptions) {
    this.client = client;
    this.currentCursor = options.after;
    this.limit = normalizePositiveInt(options.limit);
    this.type = options.type?.trim() || undefined;
    this.pollIntervalMs = normalizeNonNegativeInt(options.pollIntervalMs) ?? DEFAULT_POLL_INTERVAL_MS;
  }

  get stopped(): boolean {
    return this.stoppedValue;
  }

  [Symbol.asyncIterator](): AsyncIterator<Event> {
    return this;
  }

  async next(): Promise<IteratorResult<Event>> {
    if (this.buffer.length > 0) {
      return { done: false, value: this.buffer.shift() as Event };
    }

    while (!this.stoppedValue) {
      await this.waitForNextPollWindow();
      if (this.stoppedValue) {
        break;
      }

      let response;
      try {
        response = await this.client.pollEvents({
          after: this.currentCursor,
          limit: this.limit,
          type: this.type,
        });
      } catch (error) {
        this.stoppedValue = true;
        throw error;
      }

      this.nextPollAt = Date.now() + this.pollIntervalMs;
      if (this.stoppedValue) {
        break;
      }

      if (response.nextCursor != null) {
        this.currentCursor = response.nextCursor;
      }

      const events = response.events ?? [];
      if (events.length === 0) {
        continue;
      }

      this.buffer.push(...events);
      return { done: false, value: this.buffer.shift() as Event };
    }

    return { done: true, value: undefined };
  }

  async return(): Promise<IteratorResult<Event>> {
    await this.close();
    return { done: true, value: undefined };
  }

  async throw(error?: unknown): Promise<IteratorResult<Event>> {
    this.stop();
    throw error;
  }

  stop(): void {
    if (this.stoppedValue) {
      return;
    }
    this.stoppedValue = true;
    this.clearSleep();
  }

  async close(): Promise<void> {
    this.stop();
  }

  private async waitForNextPollWindow(): Promise<void> {
    const delayMs = this.nextPollAt - Date.now();
    if (delayMs <= 0) {
      return;
    }
    await new Promise<void>((resolve) => {
      this.sleepResolve = resolve;
      this.sleepHandle = setTimeout(() => {
        this.clearSleep();
        resolve();
      }, delayMs);
    });
  }

  private clearSleep(): void {
    if (this.sleepHandle != null) {
      clearTimeout(this.sleepHandle);
      this.sleepHandle = null;
    }
    if (this.sleepResolve != null) {
      const resolve = this.sleepResolve;
      this.sleepResolve = null;
      resolve();
    }
  }
}

function normalizePositiveInt(value: number | undefined): number | undefined {
  if (value == null || !Number.isFinite(value) || value <= 0) {
    return undefined;
  }
  return Math.floor(value);
}

function normalizeNonNegativeInt(value: number | undefined): number | undefined {
  if (value == null || !Number.isFinite(value) || value < 0) {
    return undefined;
  }
  return Math.floor(value);
}
