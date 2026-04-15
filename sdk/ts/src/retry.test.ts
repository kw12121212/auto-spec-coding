import { describe, expect, it, vi } from "vitest";
import { ApiError } from "./errors.js";
import { withRetry } from "./retry.js";

// Speed up tests by replacing setTimeout
vi.useFakeTimers();

describe("withRetry", () => {
  it("returns result immediately when first attempt succeeds", async () => {
    const fn = vi.fn().mockResolvedValue("ok");
    const result = await withRetry(fn, 3);
    expect(result).toBe("ok");
    expect(fn).toHaveBeenCalledTimes(1);
  });

  it("retries on retryable ApiError (429) and returns later success", async () => {
    const retryableErr = ApiError.fromHttpStatus(429, "rate_limited", "slow");
    const fn = vi
      .fn()
      .mockRejectedValueOnce(retryableErr)
      .mockResolvedValue("success");

    const promise = withRetry(fn, 3);
    await vi.runAllTimersAsync();
    const result = await promise;
    expect(result).toBe("success");
    expect(fn).toHaveBeenCalledTimes(2);
  });

  it("retries on retryable ApiError (500) and returns later success", async () => {
    const serverErr = ApiError.fromHttpStatus(500, "internal", "oops");
    const fn = vi
      .fn()
      .mockRejectedValueOnce(serverErr)
      .mockResolvedValue("done");

    const promise = withRetry(fn, 3);
    await vi.runAllTimersAsync();
    const result = await promise;
    expect(result).toBe("done");
    expect(fn).toHaveBeenCalledTimes(2);
  });

  it("does not retry 400 validation error", async () => {
    const clientErr = ApiError.fromHttpStatus(400, "invalid_params", "bad");
    const fn = vi.fn().mockRejectedValue(clientErr);

    await expect(withRetry(fn, 3)).rejects.toThrow(clientErr);
    expect(fn).toHaveBeenCalledTimes(1);
  });

  it("exhausts retry budget and throws last retryable error", async () => {
    const err429 = ApiError.fromHttpStatus(429, "rate_limited", "try again");
    let callCount = 0;
    const fn = vi.fn().mockImplementation(() => {
      callCount++;
      return Promise.reject(err429);
    });

    const promise = withRetry(fn, 2).catch((e: unknown) => e);
    await vi.runAllTimersAsync();
    const result = await promise;
    expect(result).toBe(err429);
    expect(callCount).toBe(3); // 1 initial + 2 retries
  });

  it("does not retry with maxRetries=0", async () => {
    const serverErr = ApiError.fromHttpStatus(503, "unavailable", "down");
    const fn = vi.fn().mockRejectedValue(serverErr);

    await expect(withRetry(fn, 0)).rejects.toThrow(serverErr);
    expect(fn).toHaveBeenCalledTimes(1);
  });

  it("does not retry non-ApiError errors", async () => {
    const err = new TypeError("unexpected");
    const fn = vi.fn().mockRejectedValue(err);

    await expect(withRetry(fn, 3)).rejects.toThrow(err);
    expect(fn).toHaveBeenCalledTimes(1);
  });
});
