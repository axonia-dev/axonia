import { afterEach, describe, expect, it, vi } from "vitest";

import { jsonResponse, urlOf } from "@/test/fetch";

import { httpPersonalAccessTokenGateway } from "./http-personal-access-token-gateway";

const gateway = httpPersonalAccessTokenGateway;

describe("httpPersonalAccessTokenGateway", () => {
  afterEach(() => vi.restoreAllMocks());

  it("lists my tokens", async () => {
    const spy = vi.spyOn(globalThis, "fetch").mockResolvedValue(
      jsonResponse([
        {
          id: 1,
          name: "cli",
          scopes: ["read"],
          createdAt: "2026-07-07T10:00:00Z",
          status: "ACTIVE",
        },
      ]),
    );

    const result = await gateway.listMine();

    expect(urlOf(spy.mock.calls[0]![0])).toContain("/axonia/me/tokens");
    expect(result[0]!.name).toBe("cli");
  });

  it("creates a token and returns the plaintext", async () => {
    const spy = vi
      .spyOn(globalThis, "fetch")
      .mockResolvedValue(jsonResponse({ token: "axp_secret" }));

    const result = await gateway.create({
      name: "cli",
      scopes: ["read", "topic:propose"],
      expiresAt: "2027-01-01T23:59:59Z",
    });

    const init = spy.mock.calls[0]![1]!;
    expect(init.method).toBe("POST");
    const parsed = JSON.parse(init.body as string) as { scopes: string[] };
    expect(parsed.scopes).toEqual(["read", "topic:propose"]);
    expect(result.token).toBe("axp_secret");
  });

  it("revokes a token by id", async () => {
    const spy = vi
      .spyOn(globalThis, "fetch")
      .mockResolvedValue(new Response(null, { status: 204 }));

    await gateway.revoke(5);

    expect(urlOf(spy.mock.calls[0]![0])).toContain("/axonia/me/tokens/5");
    expect(spy.mock.calls[0]![1]!.method).toBe("DELETE");
  });

  it("updates a token by id, sending only the name and scopes", async () => {
    const spy = vi
      .spyOn(globalThis, "fetch")
      .mockResolvedValue(new Response(null, { status: 204 }));

    await gateway.update(7, { name: "claude", scopes: ["read"] });

    expect(urlOf(spy.mock.calls[0]![0])).toContain("/axonia/me/tokens/7");
    const init = spy.mock.calls[0]![1]!;
    expect(init.method).toBe("PATCH");
    expect(JSON.parse(init.body as string)).toEqual({
      name: "claude",
      scopes: ["read"],
    });
  });

  it("never sets Content-Type at the call site: apiFetch already owns it", async () => {
    const spy = vi
      .spyOn(globalThis, "fetch")
      .mockResolvedValue(new Response(null, { status: 204 }));

    await gateway.update(7, { name: "claude", scopes: ["read"] });

    expect(
      new Headers(spy.mock.calls[0]![1]!.headers).get("content-type"),
    ).toBe("application/json");
  });

  it("reads whether this organization may connect agents", async () => {
    const spy = vi
      .spyOn(globalThis, "fetch")
      .mockResolvedValue(jsonResponse({ available: false }));

    const result = await gateway.agentAccess();

    expect(urlOf(spy.mock.calls[0]![0])).toContain("/axonia/me/agent-access");
    expect(result.available).toBe(false);
  });
});
