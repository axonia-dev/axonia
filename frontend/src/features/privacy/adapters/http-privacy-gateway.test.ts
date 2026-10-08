import { afterEach, describe, expect, it, vi } from "vitest";

import { jsonResponse, urlOf } from "@/test/fetch";

import { httpPrivacyGateway } from "./http-privacy-gateway";

describe("httpPrivacyGateway", () => {
  afterEach(() => {
    vi.restoreAllMocks();
  });

  it("fetches and parses the erasure preflight, including a null community name", async () => {
    // given
    const spy = vi.spyOn(globalThis, "fetch").mockResolvedValue(
      jsonResponse({
        permitted: false,
        blockers: [
          {
            kind: "ENTERPRISE_MEMBERSHIP",
            organizationId: "bigcorp",
            communityName: null,
          },
        ],
      }),
    );

    // when
    const actual = await httpPrivacyGateway.erasurePreflight();

    // then
    expect(urlOf(spy.mock.calls[0]?.[0])).toContain(
      "/axonia/me/account/erasure-preflight",
    );
    expect(actual).toEqual({
      permitted: false,
      blockers: [
        {
          kind: "ENTERPRISE_MEMBERSHIP",
          organizationId: "bigcorp",
          communityName: null,
        },
      ],
    });
  });

  it("erases the caller's own account with no path variable or body", async () => {
    // given
    const spy = vi
      .spyOn(globalThis, "fetch")
      .mockResolvedValue(new Response(null, { status: 204 }));

    // when
    await httpPrivacyGateway.eraseAccount();

    // then
    expect(urlOf(spy.mock.calls[0]?.[0])).toContain("/axonia/me/account");
    expect(spy.mock.calls[0]?.[1]).toMatchObject({ method: "DELETE" });
    expect(spy.mock.calls[0]?.[1]).not.toHaveProperty("body");
  });
});
