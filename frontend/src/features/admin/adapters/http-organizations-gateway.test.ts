import type { MockInstance } from "vitest";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

import { httpOrganizationsGateway } from "@/features/admin/adapters/http-organizations-gateway";

function jsonResponse(body: unknown, status = 200): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { "content-type": "application/json" },
  });
}

function hrefFrom(url: RequestInfo | URL | undefined): string {
  if (url instanceof Request) return url.url;
  if (url instanceof URL) return url.href;
  return url ?? "";
}

describe("httpOrganizationsGateway", () => {
  let fetchSpy: MockInstance<typeof fetch>;

  beforeEach(() => {
    fetchSpy = vi.spyOn(globalThis, "fetch");
  });

  afterEach(() => {
    vi.restoreAllMocks();
  });

  it("listMyOrganizations fetches and parses the caller's organizations", async () => {
    const orgs = [
      { id: "acme", name: "Acme" },
      { id: "globex", name: "Globex" },
    ];
    fetchSpy.mockResolvedValue(jsonResponse(orgs));

    const result = await httpOrganizationsGateway.listMyOrganizations();

    expect(result).toEqual(orgs);
    const [url] = fetchSpy.mock.calls[0] ?? [];
    expect(hrefFrom(url)).toContain("/axonia/me/organizations");
  });

  it("getCurrentOrganization fetches and parses the caller's current organization", async () => {
    const org = { id: "acme", name: "Acme", kind: "ENTERPRISE" };
    fetchSpy.mockResolvedValue(jsonResponse(org));

    const result = await httpOrganizationsGateway.getCurrentOrganization();

    expect(result).toEqual(org);
    const [url] = fetchSpy.mock.calls[0] ?? [];
    expect(hrefFrom(url)).toContain("/axonia/organizations/current");
  });

  it("setCommunicationsEnabled PUTs the flag and parses the returned organization", async () => {
    const org = {
      id: "acme",
      name: "Acme",
      kind: "ENTERPRISE",
      communicationsEnabled: false,
    };
    fetchSpy.mockResolvedValue(jsonResponse(org));

    const result =
      await httpOrganizationsGateway.setCommunicationsEnabled(false);

    expect(result).toEqual(org);
    const [url, init] = fetchSpy.mock.calls[0] ?? [];
    expect(hrefFrom(url)).toContain(
      "/axonia/organizations/current/communications-enabled",
    );
    expect(init?.method).toBe("PUT");
    expect(typeof init?.body === "string" ? init.body : "").toBe(
      JSON.stringify({ enabled: false }),
    );
  });
});
