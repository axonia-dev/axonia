import type { MockInstance } from "vitest";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

import { httpCommunityGateway } from "@/features/community/adapters/http-community-gateway";
import { jsonResponse, urlOf } from "@/test/fetch";

function bodyOf(init: RequestInit | undefined): string {
  return typeof init?.body === "string" ? init.body : "";
}

describe("httpCommunityGateway communication settings", () => {
  let fetchSpy: MockInstance<typeof fetch>;

  beforeEach(() => {
    fetchSpy = vi.spyOn(globalThis, "fetch");
  });

  afterEach(() => {
    vi.restoreAllMocks();
  });

  it("getCommunicationSettings fetches and parses the settings", async () => {
    // given
    const settings = {
      notifySessionUpcoming: true,
      notifySessionWrapUp: false,
      sessionUpcomingLeadDays: 5,
      wrapUpGraceHours: 72,
    };
    fetchSpy.mockResolvedValue(jsonResponse(settings));

    // when
    const actual =
      await httpCommunityGateway.getCommunicationSettings("backend-guild");

    // then
    expect(actual).toEqual(settings);
    const [url] = fetchSpy.mock.calls[0] ?? [];
    expect(urlOf(url)).toContain(
      "/axonia/communities/backend-guild/communication-settings",
    );
  });

  it("updateCommunicationSettings PUTs the settings and parses the response", async () => {
    // given
    const settings = {
      notifySessionUpcoming: false,
      notifySessionWrapUp: true,
      sessionUpcomingLeadDays: 0,
      wrapUpGraceHours: 336,
    };
    fetchSpy.mockResolvedValue(jsonResponse(settings));

    // when
    const actual = await httpCommunityGateway.updateCommunicationSettings(
      "backend-guild",
      settings,
    );

    // then
    expect(actual).toEqual(settings);
    const [url, init] = fetchSpy.mock.calls[0] ?? [];
    expect(urlOf(url)).toContain(
      "/axonia/communities/backend-guild/communication-settings",
    );
    expect(init?.method).toBe("PUT");
    expect(bodyOf(init)).toContain("wrapUpGraceHours");
  });
});
