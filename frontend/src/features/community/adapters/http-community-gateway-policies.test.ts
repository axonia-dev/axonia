import type { MockInstance } from "vitest";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

import { httpCommunityGateway } from "@/features/community/adapters/http-community-gateway";

function jsonResponse(body: unknown, status = 200): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { "content-type": "application/json" },
  });
}

function urlOf(input: RequestInfo | URL | undefined): string {
  if (input instanceof Request) {
    return input.url;
  }
  return input instanceof URL ? input.href : (input ?? "");
}

function bodyOf(init: RequestInit | undefined): string {
  return typeof init?.body === "string" ? init.body : "";
}

describe("httpCommunityGateway policies", () => {
  let fetchSpy: MockInstance<typeof fetch>;

  beforeEach(() => {
    fetchSpy = vi.spyOn(globalThis, "fetch");
  });

  afterEach(() => {
    vi.restoreAllMocks();
  });

  it("getContributionPolicy fetches and parses the policy", async () => {
    // given
    const policy = {
      canStartSeries: true,
      canEditStructure: false,
      canQueueTopics: false,
      canManageSchedule: false,
    };
    fetchSpy.mockResolvedValue(jsonResponse(policy));

    // when
    const actual =
      await httpCommunityGateway.getContributionPolicy("backend-guild");

    // then
    expect(actual).toEqual(policy);
    const [url] = fetchSpy.mock.calls[0] ?? [];
    expect(urlOf(url)).toContain(
      "/axonia/communities/backend-guild/contribution-policy",
    );
  });

  it("updateContributionPolicy PUTs the policy and parses the response", async () => {
    // given
    const policy = {
      canStartSeries: false,
      canEditStructure: true,
      canQueueTopics: true,
      canManageSchedule: false,
    };
    fetchSpy.mockResolvedValue(jsonResponse(policy));

    // when
    const actual = await httpCommunityGateway.updateContributionPolicy(
      "backend-guild",
      policy,
    );

    // then
    expect(actual).toEqual(policy);
    const [url, init] = fetchSpy.mock.calls[0] ?? [];
    expect(urlOf(url)).toContain(
      "/axonia/communities/backend-guild/contribution-policy",
    );
    expect(init?.method).toBe("PUT");
    expect(bodyOf(init)).toContain("canQueueTopics");
  });

  it("getManagementPolicy fetches and parses the policy", async () => {
    // given
    const policy = {
      canEditCommunity: true,
      canManageLookAndFeel: false,
      canManageMembers: false,
      canManageContributionPolicy: false,
      canArchiveCommunity: false,
      canCurateTags: false,
      canViewHealth: false,
      canInviteMembers: false,
      canManageCommunication: false,
      canCreditContributions: false,
    };
    fetchSpy.mockResolvedValue(jsonResponse(policy));

    // when
    const actual =
      await httpCommunityGateway.getManagementPolicy("backend-guild");

    // then
    expect(actual).toEqual(policy);
    const [url] = fetchSpy.mock.calls[0] ?? [];
    expect(urlOf(url)).toContain(
      "/axonia/communities/backend-guild/management-policy",
    );
  });

  it("updateManagementPolicy PUTs the policy and parses the response", async () => {
    // given
    const policy = {
      canEditCommunity: false,
      canManageLookAndFeel: true,
      canManageMembers: true,
      canManageContributionPolicy: false,
      canArchiveCommunity: false,
      canCurateTags: false,
      canViewHealth: false,
      canInviteMembers: false,
      canManageCommunication: false,
      canCreditContributions: false,
    };
    fetchSpy.mockResolvedValue(jsonResponse(policy));

    // when
    const actual = await httpCommunityGateway.updateManagementPolicy(
      "backend-guild",
      policy,
    );

    // then
    expect(actual).toEqual(policy);
    const [url, init] = fetchSpy.mock.calls[0] ?? [];
    expect(urlOf(url)).toContain(
      "/axonia/communities/backend-guild/management-policy",
    );
    expect(init?.method).toBe("PUT");
    expect(bodyOf(init)).toContain("canManageMembers");
    // Pins a regression: the ninth axis must ride along on every
    // save, or the backend's @NotNull Boolean rejects the request with a 400.
    expect(bodyOf(init)).toContain("canManageCommunication");
  });
});
