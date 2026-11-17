import type { MockInstance } from "vitest";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

import { httpAdminGateway } from "@/features/admin/adapters/http-admin-gateway";

function jsonResponse(body: unknown): Response {
  return new Response(JSON.stringify(body), {
    status: 200,
    headers: { "content-type": "application/json" },
  });
}

function requestOf(spy: MockInstance<typeof fetch>) {
  const [url, init] = spy.mock.calls[0] ?? [];
  return { url: String(url), init };
}

describe("httpAdminGateway member names", () => {
  let fetchSpy: MockInstance<typeof fetch>;

  beforeEach(() => {
    fetchSpy = vi.spyOn(globalThis, "fetch");
  });

  afterEach(() => {
    vi.restoreAllMocks();
  });

  it("reads the stored patterns with a GET", async () => {
    fetchSpy.mockResolvedValue(
      jsonResponse({ patterns: ["{family}, {given}-ACME"] }),
    );

    const actual = await httpAdminGateway.getMemberNamePatterns();

    expect(actual).toEqual(["{family}, {given}-ACME"]);
    const { url, init } = requestOf(fetchSpy);
    expect(url).toContain("/axonia/member-names");
    expect(init?.method).toBeUndefined();
  });

  it("posts the patterns to the preview and returns its rows", async () => {
    const rows = [
      {
        received: "Rochat, Aurélia-ACME",
        displayName: "Aurélia Rochat",
        givenName: "Aurélia",
        matched: 0,
      },
      {
        received: "octocat",
        displayName: "octocat",
        givenName: null,
        matched: null,
      },
    ];
    fetchSpy.mockResolvedValue(jsonResponse({ rows }));

    const actual = await httpAdminGateway.previewMemberNames(["{given}"]);

    expect(actual).toEqual(rows);
    const { url, init } = requestOf(fetchSpy);
    expect(url).toContain("/axonia/member-names/preview");
    expect(init?.method).toBe("POST");
    expect(init?.body).toBe(JSON.stringify({ patterns: ["{given}"] }));
  });

  it("puts the patterns and returns how many names changed", async () => {
    fetchSpy.mockResolvedValue(jsonResponse({ changed: 2 }));

    const actual = await httpAdminGateway.saveMemberNamePatterns(["a"]);

    expect(actual).toBe(2);
    const { url, init } = requestOf(fetchSpy);
    expect(url).toMatch(/\/axonia\/member-names$/);
    expect(init?.method).toBe("PUT");
    expect(init?.body).toBe(JSON.stringify({ patterns: ["a"] }));
  });

  it("sets no Content-Type of its own beyond the client default", async () => {
    fetchSpy.mockResolvedValue(jsonResponse({ changed: 0 }));

    await httpAdminGateway.saveMemberNamePatterns([]);

    const headers = requestOf(fetchSpy).init?.headers as Record<string, string>;
    expect(Object.keys(headers).filter((k) => /content-type/i.test(k))).toEqual(
      ["content-type"],
    );
  });

  it("rejects a response of the wrong shape", async () => {
    fetchSpy.mockResolvedValue(jsonResponse({ patterns: "nope" }));

    await expect(httpAdminGateway.getMemberNamePatterns()).rejects.toThrow();
  });
});
