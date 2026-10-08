import type { MockInstance } from "vitest";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

import { httpAdminGateway } from "@/features/admin/adapters/http-admin-gateway";

// Split out of http-admin-gateway.test.ts (which was pushing eslint's max-lines): the users
// listing, whose response is a page envelope rather than a bare array like every other call
// on this gateway.
function jsonResponse(body: unknown, status = 200): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { "content-type": "application/json" },
  });
}

function urlOf(input: RequestInfo | URL | undefined): string {
  if (input instanceof Request) return input.url;
  return input instanceof URL ? input.href : (input ?? "");
}

const adminUser = {
  userId: "u1",
  displayName: "Alice",
  email: "alice@example.com",
  memberStatus: "PRESENT",
  effectiveRoles: [
    {
      role: "ADMIN",
      via: "DIRECT",
      bindingId: 1,
      roleId: 10,
    },
  ],
};

function adminUserPage(
  content: unknown[],
  page: { totalElements?: number; totalPages?: number } = {},
) {
  return {
    content,
    page: {
      size: 20,
      number: 0,
      totalElements: page.totalElements ?? content.length,
      totalPages: page.totalPages ?? 1,
    },
  };
}

describe("httpAdminGateway listUsers", () => {
  let fetchSpy: MockInstance<typeof fetch>;

  beforeEach(() => {
    fetchSpy = vi.spyOn(globalThis, "fetch");
  });

  afterEach(() => {
    vi.restoreAllMocks();
  });

  it("fetches /axonia/admin/users and parses the result", async () => {
    // given
    fetchSpy.mockResolvedValue(jsonResponse(adminUserPage([adminUser])));

    // when
    const actual = await httpAdminGateway.listUsers({});

    // then
    expect(actual.content[0]?.userId).toBe("u1");
    const [url] = fetchSpy.mock.calls[0] ?? [];
    expect(urlOf(url)).toContain("/axonia/admin/users");
  });

  it("reads the roster total from the envelope, not the length of the page", async () => {
    // given - one row on this page, but 42 people in the organization
    fetchSpy.mockResolvedValue(
      jsonResponse(
        adminUserPage([adminUser], { totalElements: 42, totalPages: 3 }),
      ),
    );

    // when
    const actual = await httpAdminGateway.listUsers({});

    // then
    expect(actual.content).toHaveLength(1);
    expect(actual.totalElements).toBe(42);
    expect(actual.totalPages).toBe(3);
  });

  it("encodes query params into the URL", async () => {
    // given
    fetchSpy.mockResolvedValue(jsonResponse(adminUserPage([adminUser])));

    // when
    await httpAdminGateway.listUsers({ q: "alice", page: 0, size: 20 });

    // then
    const [url] = fetchSpy.mock.calls[0] ?? [];
    expect(urlOf(url)).toContain("q=alice");
    expect(urlOf(url)).toContain("page=0");
    expect(urlOf(url)).toContain("size=20");
  });

  it("rejects when the response is missing required fields", async () => {
    // given
    fetchSpy.mockResolvedValue(jsonResponse(adminUserPage([{ userId: "u1" }])));

    // when / then
    await expect(httpAdminGateway.listUsers({})).rejects.toThrow();
  });

  it("rejects a bare array, which carries no total", async () => {
    // given
    fetchSpy.mockResolvedValue(jsonResponse([adminUser]));

    // when / then
    await expect(httpAdminGateway.listUsers({})).rejects.toThrow();
  });
});
