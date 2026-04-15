import { describe, expect, it } from "vitest";
import { ApiError, isRetryableStatus } from "./errors.js";

describe("ApiError", () => {
  it("sets status, code, retryable fields from HTTP status", () => {
    const err = ApiError.fromHttpStatus(400, "invalid_params", "Bad prompt");
    expect(err.status).toBe(400);
    expect(err.code).toBe("invalid_params");
    expect(err.message).toBe("specdriven: http 400 invalid_params: Bad prompt");
    expect(err.retryable).toBe(false);
    expect(err).toBeInstanceOf(ApiError);
    expect(err).toBeInstanceOf(Error);
  });

  it("marks 429 as retryable", () => {
    const err = ApiError.fromHttpStatus(429, "rate_limited", "slow down");
    expect(err.retryable).toBe(true);
  });

  it("marks 500 as retryable", () => {
    const err = ApiError.fromHttpStatus(500, "internal", "oops");
    expect(err.retryable).toBe(true);
  });

  it("marks 503 as retryable", () => {
    const err = ApiError.fromHttpStatus(503, "unavailable", "down");
    expect(err.retryable).toBe(true);
  });

  it("marks 400 as non-retryable", () => {
    expect(ApiError.fromHttpStatus(400, "bad", "").retryable).toBe(false);
  });

  it("marks 401 as non-retryable", () => {
    expect(ApiError.fromHttpStatus(401, "unauthorized", "").retryable).toBe(false);
  });

  it("marks 403 as non-retryable", () => {
    expect(ApiError.fromHttpStatus(403, "forbidden", "").retryable).toBe(false);
  });

  it("marks 404 as non-retryable", () => {
    expect(ApiError.fromHttpStatus(404, "not_found", "").retryable).toBe(false);
  });

  it("marks 422 as non-retryable", () => {
    expect(ApiError.fromHttpStatus(422, "unprocessable", "").retryable).toBe(false);
  });

  it("fromNetworkError sets retryable true and preserves cause", () => {
    const cause = new TypeError("fetch failed");
    const err = ApiError.fromNetworkError(cause);
    expect(err.retryable).toBe(true);
    expect(err.code).toBe("network_error");
    expect(err.cause).toBe(cause);
    expect(err.status).toBe(0);
  });

  it("fromNetworkError with non-Error cause uses fallback message", () => {
    const err = ApiError.fromNetworkError("string cause");
    expect(err.message).toBe("specdriven: string cause");
  });

  it("formats message without code", () => {
    const err = ApiError.fromHttpStatus(500, "", "unexpected error");
    expect(err.message).toBe("specdriven: http 500: unexpected error");
  });

  it("formats message without message or code", () => {
    const err = ApiError.fromHttpStatus(503, "", "");
    // falls back to status text lookup
    expect(err.message).toContain("503");
  });
});

describe("isRetryableStatus", () => {
  it("returns true for 429", () => {
    expect(isRetryableStatus(429)).toBe(true);
  });

  it("returns true for 500+", () => {
    expect(isRetryableStatus(500)).toBe(true);
    expect(isRetryableStatus(503)).toBe(true);
  });

  it("returns false for 4xx client errors", () => {
    expect(isRetryableStatus(400)).toBe(false);
    expect(isRetryableStatus(401)).toBe(false);
    expect(isRetryableStatus(404)).toBe(false);
  });
});
