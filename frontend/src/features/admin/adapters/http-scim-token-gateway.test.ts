import type { MockInstance } from "vitest";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

import { httpScimTokenGateway } from "@/features/admin/adapters/http-scim-token-gateway";

function jsonResponse(body: unknown, status = 200): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { "content-type": "application/json" },
  });
}

function emptyResponse(status = 204): Response {
  return new Response(null, { status });
}

function hrefFrom(url: RequestInfo | URL | undefined): string {
  if (url instanceof Request) return url.url;
  if (url instanceof URL) return url.href;
  return url ?? "";
}

describe("httpScimTokenGateway", () => {
  let fetchSpy: MockInstance<typeof fetch>;

  beforeEach(() => {
    fetchSpy = vi.spyOn(globalThis, "fetch");
  });

  afterEach(() => {
    vi.restoreAllMocks();
  });

  it("generateToken posts the label and the expiry", async () => {
    fetchSpy.mockResolvedValue(jsonResponse({ token: "secret-xyz" }));

    const result = await httpScimTokenGateway.generateToken({
      label: "Test",
      expiresAt: "2027-01-01T23:59:59Z",
    });

    expect(result).toEqual({ token: "secret-xyz" });
    const [url, init] = fetchSpy.mock.calls[0] ?? [];
    expect(hrefFrom(url)).toContain("/axonia/scim-tokens");
    expect(init?.method).toBe("POST");
    const sentBody = init?.body;
    expect(typeof sentBody).toBe("string");
    expect(JSON.parse(sentBody as string)).toEqual({
      label: "Test",
      expiresAt: "2027-01-01T23:59:59Z",
    });
  });

  it("generateToken refuses a past expiry before ever calling fetch", async () => {
    await expect(
      httpScimTokenGateway.generateToken({
        label: "Test",
        expiresAt: "2020-01-01T23:59:59Z",
      }),
    ).rejects.toThrow();
    expect(fetchSpy).not.toHaveBeenCalled();
  });

  it("revokeToken deletes by token id", async () => {
    fetchSpy.mockResolvedValue(emptyResponse());

    await httpScimTokenGateway.revokeToken(42);

    const [url, init] = fetchSpy.mock.calls[0] ?? [];
    expect(hrefFrom(url)).toContain("/axonia/scim-tokens/42");
    expect(init?.method).toBe("DELETE");
  });

  it("listTokens parses the ScimTokenView shape the backend returns", async () => {
    fetchSpy.mockResolvedValue(
      jsonResponse([
        {
          id: 7,
          label: "Entra provisioning",
          lastUsedAt: null,
          expiresAt: "2027-01-01T00:00:00Z",
          createdAt: "2026-07-19T00:00:00Z",
          status: "ACTIVE",
        },
      ]),
    );

    const result = await httpScimTokenGateway.listTokens();

    expect(result).toEqual([
      {
        id: 7,
        label: "Entra provisioning",
        lastUsedAt: null,
        expiresAt: "2027-01-01T00:00:00Z",
        createdAt: "2026-07-19T00:00:00Z",
        status: "ACTIVE",
      },
    ]);
    const [url] = fetchSpy.mock.calls[0] ?? [];
    expect(hrefFrom(url)).toContain("/axonia/scim-tokens");
    expect(hrefFrom(url)).not.toContain("organizationId");
  });
});
