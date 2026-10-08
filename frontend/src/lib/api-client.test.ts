import { afterEach, describe, expect, it, vi } from "vitest";

import {
  setAccessToken,
  setOrganizationOfflineHandler,
  setUnauthorizedHandler,
} from "@/auth/token";
import {
  ApiError,
  apiFetch,
  apiFetchOptional,
  apiFetchVoid,
} from "@/lib/api-client";

afterEach(() => {
  vi.restoreAllMocks();
});

describe("apiFetch", () => {
  it("returns parsed JSON on success", async () => {
    vi.spyOn(globalThis, "fetch").mockResolvedValue(Response.json({ id: 1 }));
    const data = await apiFetch<{ id: number }>("/guilds");
    expect(data).toEqual({ id: 1 });
  });

  it("prefixes the configured base url", async () => {
    const spy = vi
      .spyOn(globalThis, "fetch")
      .mockResolvedValue(Response.json({}));
    await apiFetch("/guilds");
    const [requestInfo] = spy.mock.calls[0] ?? [];
    expect(typeof requestInfo === "string" ? requestInfo : "").toContain(
      "/guilds",
    );
  });

  it("sends the X-API-Version header on every request", async () => {
    const spy = vi
      .spyOn(globalThis, "fetch")
      .mockResolvedValue(Response.json({}));
    await apiFetch("/guilds");
    const [, init] = spy.mock.calls[0] ?? [];
    const headers = new Headers(init?.headers);
    expect(headers.get("x-api-version")).toBe("1");
  });

  it("throws ApiError with the status on a non-2xx response", async () => {
    vi.spyOn(globalThis, "fetch").mockResolvedValue(
      new Response("nope", { status: 404 }),
    );
    await expect(apiFetch("/missing")).rejects.toBeInstanceOf(ApiError);
    await expect(apiFetch("/missing")).rejects.toMatchObject({ status: 404 });
  });
});

describe("apiFetchVoid", () => {
  it("resolves with no value on a 204 response", async () => {
    // given
    vi.spyOn(globalThis, "fetch").mockResolvedValue(
      new Response(null, { status: 204 }),
    );

    // when / then
    await expect(
      apiFetchVoid("/communities/x/members/me", { method: "DELETE" }),
    ).resolves.toBeUndefined();
  });

  it("throws ApiError on a non-2xx response", async () => {
    // given
    vi.spyOn(globalThis, "fetch").mockResolvedValue(
      new Response("nope", { status: 409 }),
    );

    // when / then
    await expect(
      apiFetchVoid("/communities/x/members/me", { method: "DELETE" }),
    ).rejects.toBeInstanceOf(ApiError);
  });
});

describe("ProblemDetail handling", () => {
  it("attaches the ProblemDetail code and detail to ApiError on failure", async () => {
    // given
    vi.spyOn(globalThis, "fetch").mockResolvedValue(
      new Response(
        JSON.stringify({ detail: "Already a member", code: "ALREADY_MEMBER" }),
        { status: 409, headers: { "content-type": "application/json" } },
      ),
    );

    // when
    const actual = await apiFetch("/communities/x/members").catch(
      (e: unknown) => e,
    );

    // then - `detail` is kept separate from `message` (which may be a synthesized fallback, see
    // below) so a caller can show the server's own reason without ever risking that fallback.
    expect(actual).toBeInstanceOf(ApiError);
    const error = actual as ApiError;
    expect(error.status).toBe(409);
    expect(error.code).toBe("ALREADY_MEMBER");
    expect(error.message).toBe("Already a member");
    expect(error.detail).toBe("Already a member");
  });

  it("falls back to a generic message when the error body is not JSON", async () => {
    // given
    vi.spyOn(globalThis, "fetch").mockResolvedValue(
      new Response("oops", { status: 500 }),
    );

    // when
    const actual = await apiFetch("/communities").catch((e: unknown) => e);

    // then - no detail was supplied, so none is invented
    expect(actual).toBeInstanceOf(ApiError);
    expect((actual as ApiError).code).toBeUndefined();
    expect((actual as ApiError).detail).toBeUndefined();
  });
});

describe("organization header", () => {
  it("never sends the dead x-axonia-organization header", async () => {
    // given - the active org now rides the signed token claim, not a header
    const spy = vi
      .spyOn(globalThis, "fetch")
      .mockResolvedValue(Response.json({}));

    // when
    await apiFetch("/guilds");

    // then
    const [, init] = spy.mock.calls[0] ?? [];
    const headers = new Headers(init?.headers);
    expect(headers.get("x-axonia-organization")).toBeNull();
  });
});

describe("authentication", () => {
  afterEach(() => {
    setAccessToken(undefined);
    setUnauthorizedHandler(undefined);
  });

  it("attaches the Bearer token when one is set", async () => {
    // given
    setAccessToken("token-123");
    const spy = vi
      .spyOn(globalThis, "fetch")
      .mockResolvedValue(Response.json({}));

    // when
    await apiFetch("/axonia/me");

    // then
    const [, init] = spy.mock.calls[0] ?? [];
    const headers = new Headers(init?.headers);
    expect(headers.get("authorization")).toBe("Bearer token-123");
  });

  it("sends no authorization header without a token", async () => {
    // given
    const spy = vi
      .spyOn(globalThis, "fetch")
      .mockResolvedValue(Response.json({}));

    // when
    await apiFetch("/axonia/communities");

    // then
    const [, init] = spy.mock.calls[0] ?? [];
    const headers = new Headers(init?.headers);
    expect(headers.get("authorization")).toBeNull();
  });

  it("notifies the unauthorized handler on a 401, with the token the refused request carried", async () => {
    // given a renewal that lands while the request is out
    setAccessToken("the-sent-token");
    const handler = vi.fn();
    setUnauthorizedHandler(handler);
    vi.spyOn(globalThis, "fetch").mockImplementation(() => {
      setAccessToken("a-fresh-token");
      return Promise.resolve(new Response("{}", { status: 401 }));
    });

    // when
    const actual = await apiFetch("/axonia/me").catch((e: unknown) => e);

    // then - still surfaces the ApiError AND hands the auth layer the token that was refused
    expect(actual).toBeInstanceOf(ApiError);
    expect(handler).toHaveBeenCalledExactlyOnceWith(
      "the-sent-token",
      undefined,
    );
  });

  it("passes the ProblemDetail code of a 401 to the unauthorized handler", async () => {
    // given
    const handler = vi.fn();
    setUnauthorizedHandler(handler);
    vi.spyOn(globalThis, "fetch").mockResolvedValue(
      new Response(
        JSON.stringify({
          detail: "This session was revoked.",
          code: "session_revoked",
        }),
        { status: 401, headers: { "content-type": "application/json" } },
      ),
    );

    // when
    const actual = await apiFetch("/axonia/me").catch((e: unknown) => e);

    // then
    expect(handler).toHaveBeenCalledWith(undefined, "session_revoked");
    expect((actual as ApiError).code).toBe("session_revoked");
    expect((actual as ApiError).message).toBe("This session was revoked.");
  });
});

describe("an organization that is not serving", () => {
  afterEach(() => {
    setOrganizationOfflineHandler(undefined);
  });

  function refuseWith(code: string) {
    vi.spyOn(globalThis, "fetch").mockResolvedValue(
      new Response(JSON.stringify({ code }), {
        status: 403,
        headers: { "content-type": "application/problem+json" },
      }),
    );
  }

  it.each([
    ["organization_suspended", "SUSPENDED"],
    ["organization_closed", "CLOSED"],
  ])("translates %s into the %s screen", async (code, reason) => {
    const onOffline = vi.fn();
    setOrganizationOfflineHandler(onOffline);
    refuseWith(code);

    await expect(apiFetch("/anything")).rejects.toBeInstanceOf(ApiError);

    expect(onOffline).toHaveBeenCalledExactlyOnceWith(reason);
  });

  it("leaves the seam alone for any other 403", async () => {
    const onOffline = vi.fn();
    setOrganizationOfflineHandler(onOffline);
    refuseWith("forbidden");

    await expect(apiFetch("/anything")).rejects.toBeInstanceOf(ApiError);

    expect(onOffline).not.toHaveBeenCalled();
  });

  it("leaves the seam alone when the same code arrives on another status", async () => {
    const onOffline = vi.fn();
    setOrganizationOfflineHandler(onOffline);
    vi.spyOn(globalThis, "fetch").mockResolvedValue(
      new Response(JSON.stringify({ code: "organization_suspended" }), {
        status: 500,
      }),
    );

    await expect(apiFetch("/anything")).rejects.toBeInstanceOf(ApiError);

    expect(onOffline).not.toHaveBeenCalled();
  });
});

describe("apiFetchOptional", () => {
  it("resolves to null on a 204 with no body", async () => {
    // given
    vi.spyOn(globalThis, "fetch").mockResolvedValue(
      new Response(null, { status: 204 }),
    );

    // when
    const actual = await apiFetchOptional("/organizations/current/thing");

    // then
    expect(actual).toBeNull();
  });

  it("returns the parsed JSON when there is a body", async () => {
    // given
    vi.spyOn(globalThis, "fetch").mockResolvedValue(Response.json({ id: 1 }));

    // when
    const actual = await apiFetchOptional<{ id: number }>("/things/1");

    // then
    expect(actual).toEqual({ id: 1 });
  });
});
