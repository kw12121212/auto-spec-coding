import { ApiError } from "./errors.js";

/**
 * Retries fn up to maxRetries additional times (total attempts = maxRetries + 1)
 * when the thrown error is a retryable ApiError.
 * Uses exponential backoff starting at 100ms.
 */
export async function withRetry<T>(
  fn: () => Promise<T>,
  maxRetries: number,
): Promise<T> {
  let lastError: ApiError | undefined;
  for (let attempt = 0; attempt <= maxRetries; attempt++) {
    if (attempt > 0) {
      const delayMs = 100 * Math.pow(2, attempt - 1);
      await sleep(delayMs);
    }
    try {
      return await fn();
    } catch (err) {
      if (err instanceof ApiError && err.retryable && attempt < maxRetries) {
        lastError = err;
        continue;
      }
      throw err;
    }
  }
  // Only reachable when maxRetries >= 1 and every attempt threw a retryable error.
  throw lastError!;
}

function sleep(ms: number): Promise<void> {
  return new Promise((resolve) => setTimeout(resolve, ms));
}
