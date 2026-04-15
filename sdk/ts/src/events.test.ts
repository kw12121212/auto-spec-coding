import { afterEach, describe, expect, it, vi } from "vitest";
import { createEventSubscription } from "./events.js";
import type { Event, PollEventsOptions } from "./models.js";

afterEach(() => {
  vi.useRealTimers();
});

function makeEvent(sequence: number, type = "AGENT_STATE_CHANGED"): Event {
  return {
    sequence,
    type,
    timestamp: sequence * 100,
    source: "agent",
    metadata: { sequence },
  };
}

describe("createEventSubscription", () => {
  it("starts from an explicit cursor and continues from nextCursor", async () => {
    const calls: PollEventsOptions[] = [];
    const pollEvents = vi
      .fn()
      .mockImplementationOnce(async (options: PollEventsOptions = {}) => {
        calls.push(options);
        return { events: [makeEvent(11)], nextCursor: 11 };
      })
      .mockImplementationOnce(async (options: PollEventsOptions = {}) => {
        calls.push(options);
        return { events: [makeEvent(12)], nextCursor: 12 };
      });

    const subscription = createEventSubscription(
      { pollEvents },
      { after: 10, limit: 5, type: "AGENT_STATE_CHANGED", pollIntervalMs: 0 },
    );

    await expect(subscription.next()).resolves.toEqual({ done: false, value: makeEvent(11) });
    await expect(subscription.next()).resolves.toEqual({ done: false, value: makeEvent(12) });

    expect(calls).toEqual([
      { after: 10, limit: 5, type: "AGENT_STATE_CHANGED" },
      { after: 11, limit: 5, type: "AGENT_STATE_CHANGED" },
    ]);
  });

  it("continues polling after an empty result without emitting synthetic events", async () => {
    const calls: PollEventsOptions[] = [];
    const pollEvents = vi
      .fn()
      .mockImplementationOnce(async (options: PollEventsOptions = {}) => {
        calls.push(options);
        return { events: [], nextCursor: 20 };
      })
      .mockImplementationOnce(async (options: PollEventsOptions = {}) => {
        calls.push(options);
        return { events: [makeEvent(21)], nextCursor: 21 };
      });

    const subscription = createEventSubscription({ pollEvents }, { after: 10, pollIntervalMs: 0 });

    await expect(subscription.next()).resolves.toEqual({ done: false, value: makeEvent(21) });
    expect(calls).toEqual([{ after: 10, limit: undefined, type: undefined }, { after: 20, limit: undefined, type: undefined }]);
  });

  it("stops cleanly and issues no further polls", async () => {
    const pollEvents = vi.fn().mockResolvedValue({ events: [makeEvent(1)], nextCursor: 1 });
    const subscription = createEventSubscription({ pollEvents }, { pollIntervalMs: 0 });

    await expect(subscription.next()).resolves.toEqual({ done: false, value: makeEvent(1) });
    subscription.stop();
    await expect(subscription.next()).resolves.toEqual({ done: true, value: undefined });

    expect(subscription.stopped).toBe(true);
    expect(pollEvents).toHaveBeenCalledTimes(1);
  });

  it("waits for the configured poll interval between completed polls", async () => {
    vi.useFakeTimers();
    const callTimes: number[] = [];
    const pollEvents = vi
      .fn()
      .mockImplementation(async () => {
        callTimes.push(Date.now());
        return { events: [], nextCursor: callTimes.length };
      });

    const subscription = createEventSubscription({ pollEvents }, { pollIntervalMs: 50 });
    const nextPromise = subscription.next();

    await vi.advanceTimersByTimeAsync(49);
    expect(pollEvents).toHaveBeenCalledTimes(1);

    await vi.advanceTimersByTimeAsync(1);
    expect(pollEvents).toHaveBeenCalledTimes(2);

    subscription.stop();
    await expect(nextPromise).resolves.toEqual({ done: true, value: undefined });
    expect(callTimes[1] - callTimes[0]).toBeGreaterThanOrEqual(50);
  });

  it("surfaces polling failures and stops the subscription", async () => {
    const failure = new Error("backend unavailable");
    const pollEvents = vi.fn().mockRejectedValue(failure);
    const subscription = createEventSubscription({ pollEvents }, { pollIntervalMs: 0 });

    await expect(subscription.next()).rejects.toThrow("backend unavailable");
    expect(subscription.stopped).toBe(true);
    await expect(subscription.next()).resolves.toEqual({ done: true, value: undefined });
  });
});
