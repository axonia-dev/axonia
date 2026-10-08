import { afterEach, describe, expect, it, vi } from "vitest";

import { ApiError } from "@/lib/api-client";
import { urlOf } from "@/test/fetch";

import { httpUnsubscribeGateway } from "./http-unsubscribe-gateway";

describe("httpUnsubscribeGateway", () => {
  afterEach(() => vi.restoreAllMocks());

  it("POSTs the token to the public unsubscribe endpoint", async () => {
    const spy = vi
      .spyOn(globalThis, "fetch")
      .mockResolvedValue(new Response(null, { status: 204 }));

    await httpUnsubscribeGateway.unsubscribe("abc");

    const url = urlOf(spy.mock.calls[0]![0]);
    expect(url).toContain("/public/notifications/unsubscribe");
    expect(url).toContain("token=abc");
    expect(spy.mock.calls[0]![1]!.method).toBe("POST");
  });

  it("POSTs with no token param when the link carried none", async () => {
    const spy = vi
      .spyOn(globalThis, "fetch")
      .mockResolvedValue(new Response(null, { status: 400 }));

    await expect(httpUnsubscribeGateway.unsubscribe(null)).rejects.toThrow(
      ApiError,
    );

    const url = urlOf(spy.mock.calls[0]![0]);
    expect(url).not.toContain("token=");
  });

  it("rejects with the backend's status when the request fails", async () => {
    vi.spyOn(globalThis, "fetch").mockResolvedValue(
      new Response(null, { status: 429 }),
    );

    const error = await httpUnsubscribeGateway
      .unsubscribe("abc")
      .catch((caught: unknown) => caught);

    expect(error).toBeInstanceOf(ApiError);
    expect((error as ApiError).status).toBe(429);
  });
});
