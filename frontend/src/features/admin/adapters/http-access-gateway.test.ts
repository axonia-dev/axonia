import type { MockInstance } from "vitest";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

import { httpAccessGateway } from "@/features/admin/adapters/http-access-gateway";

function jsonResponse(body: unknown, status = 200): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { "content-type": "application/json" },
  });
}

describe("httpAccessGateway", () => {
  let fetchSpy: MockInstance<typeof fetch>;

  beforeEach(() => {
    fetchSpy = vi.spyOn(globalThis, "fetch");
  });

  afterEach(() => {
    vi.restoreAllMocks();
  });

  it("getMe fetches /axonia/access/me and parses the result", async () => {
    // given
    const payload = {
      permissions: ["community_kind:read", "community:read"],
      roles: [
        { id: 7, name: "Admin" },
        { id: 9, name: "Auditor" },
      ],
    };
    fetchSpy.mockResolvedValue(jsonResponse(payload));

    // when
    const actual = await httpAccessGateway.getMe();

    // then
    expect(actual).toEqual({
      permissions: ["community_kind:read", "community:read"],
      roles: [
        { id: 7, name: "Admin" },
        { id: 9, name: "Auditor" },
      ],
    });
    const [url] = fetchSpy.mock.calls[0] ?? [];
    let href = "";
    if (url instanceof Request) {
      href = url.url;
    } else if (url instanceof URL) {
      href = url.href;
    } else {
      href = url ?? "";
    }
    expect(href).toContain("/axonia/access/me");
  });

  it("getMe defaults roles to an empty array when the payload omits it", async () => {
    // given - the older backend contract, which never sends the roles key at all
    fetchSpy.mockResolvedValue(
      jsonResponse({ permissions: ["community_kind:read"] }),
    );

    // when
    const actual = await httpAccessGateway.getMe();

    // then
    expect(actual.roles).toEqual([]);
  });

  it("getMe rejects when the response is missing permissions", async () => {
    // given
    fetchSpy.mockResolvedValue(jsonResponse({}));

    // when / then
    await expect(httpAccessGateway.getMe()).rejects.toThrow();
  });
});
