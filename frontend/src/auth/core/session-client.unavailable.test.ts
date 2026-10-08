import { afterEach, describe, expect, it, vi } from "vitest";

import { renewSession } from "./session-client";

afterEach(() => {
  vi.restoreAllMocks();
});

describe("renewSession, when the session cannot be renewed now", () => {
  // A 503 is the edge saying the identity provider could not be reached, with the cookie kept.
  it.each([500, 502, 503, 504])(
    "reports the session unavailable, not ended, when the edge answers %i, which never clears the cookie",
    async (status) => {
      // given
      vi.spyOn(globalThis, "fetch").mockResolvedValue(
        new Response(null, { status }),
      );

      // when
      const renewed = await renewSession();

      // then
      expect(renewed).toEqual({ unavailable: true, retryAfterSeconds: null });
    },
  );

  it("passes on how long the edge asked to be left alone", async () => {
    // given
    vi.spyOn(globalThis, "fetch").mockResolvedValue(
      new Response(null, { status: 503, headers: { "Retry-After": "120" } }),
    );

    // when
    const renewed = await renewSession();

    // then
    expect(renewed).toEqual({ unavailable: true, retryAfterSeconds: 120 });
  });

  it("reports the session unavailable when the edge itself cannot be reached", async () => {
    // given
    vi.spyOn(globalThis, "fetch").mockRejectedValue(
      new TypeError("Failed to fetch"),
    );

    // when
    const renewed = await renewSession();

    // then
    expect(renewed).toEqual({ unavailable: true, retryAfterSeconds: null });
  });

  it.each(["Wed, 21 Oct 2026 07:28:00 GMT", "120abc"])(
    "ignores a Retry-After that is not a number of seconds (%s)",
    async (retryAfter) => {
      // given
      vi.spyOn(globalThis, "fetch").mockResolvedValue(
        new Response(null, {
          status: 503,
          headers: { "Retry-After": retryAfter },
        }),
      );

      // when
      const renewed = await renewSession();

      // then
      expect(renewed).toEqual({ unavailable: true, retryAfterSeconds: null });
    },
  );
});
