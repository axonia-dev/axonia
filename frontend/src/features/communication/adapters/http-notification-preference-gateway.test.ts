import { afterEach, describe, expect, it, vi } from "vitest";
import { ZodError } from "zod";

import { jsonResponse, urlOf } from "@/test/fetch";

import { httpNotificationPreferenceGateway } from "./http-notification-preference-gateway";

const gateway = httpNotificationPreferenceGateway;

describe("httpNotificationPreferenceGateway", () => {
  afterEach(() => vi.restoreAllMocks());

  it("gets my preferences", async () => {
    const spy = vi.spyOn(globalThis, "fetch").mockResolvedValue(
      jsonResponse([
        { type: "session_upcoming", enabled: true },
        { type: "session_wrap_up", enabled: false },
        { type: "session_awaiting_close", enabled: true },
      ]),
    );

    const result = await gateway.getMine();

    expect(urlOf(spy.mock.calls[0]![0])).toContain(
      "/axonia/me/notification-preferences",
    );
    expect(result).toHaveLength(3);
    expect(result[1]).toEqual({ type: "session_wrap_up", enabled: false });
  });

  it("drops a row of a message type this build does not know about, keeping the rest", async () => {
    vi.spyOn(globalThis, "fetch").mockResolvedValue(
      jsonResponse([
        { type: "session_upcoming", enabled: true },
        { type: "session_new_and_unreleased", enabled: true },
        { type: "session_wrap_up", enabled: false },
      ]),
    );

    const result = await gateway.getMine();

    expect(result).toEqual([
      { type: "session_upcoming", enabled: true },
      { type: "session_wrap_up", enabled: false },
    ]);
  });

  it("throws when a known type's row is otherwise malformed, rather than silently dropping it", async () => {
    vi.spyOn(globalThis, "fetch").mockResolvedValue(
      jsonResponse([
        { type: "session_upcoming", enabled: "false" },
        { type: "session_wrap_up", enabled: false },
      ]),
    );

    await expect(gateway.getMine()).rejects.toThrow(ZodError);
  });

  it("throws on a row that isn't an object at all, rather than silently dropping it", async () => {
    vi.spyOn(globalThis, "fetch").mockResolvedValue(
      jsonResponse([null, null, null]),
    );

    await expect(gateway.getMine()).rejects.toThrow(ZodError);
  });

  it("throws on a row whose type field is missing entirely (renamed), rather than reading it as unrecognised", async () => {
    vi.spyOn(globalThis, "fetch").mockResolvedValue(
      jsonResponse([
        { messageType: "session_upcoming", enabled: true },
        { type: "session_wrap_up", enabled: false },
      ]),
    );

    await expect(gateway.getMine()).rejects.toThrow(ZodError);
  });

  it("PUTs a single preference change", async () => {
    const spy = vi
      .spyOn(globalThis, "fetch")
      .mockResolvedValue(new Response(null, { status: 204 }));

    await gateway.setMine("session_wrap_up", false);

    expect(urlOf(spy.mock.calls[0]![0])).toContain(
      "/axonia/me/notification-preferences",
    );
    const init = spy.mock.calls[0]![1]!;
    expect(init.method).toBe("PUT");
    expect(JSON.parse(init.body as string)).toEqual({
      type: "session_wrap_up",
      enabled: false,
    });
  });
});
