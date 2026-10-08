import { renderHook } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";

import type { ManagementAccess } from "@/features/community/api/use-management-access";
import { useManagementAccess } from "@/features/community/api/use-management-access";
import { useManagementPolicyQuery } from "@/features/community/api/use-management-policy";
import { useMyMembership } from "@/features/community/api/use-membership";

vi.mock("@/features/community/api/use-membership", () => ({
  useMyMembership: vi.fn(),
}));
vi.mock("@/features/community/api/use-management-policy", () => ({
  useManagementPolicyQuery: vi.fn(),
}));

const closedPolicy = {
  canEditCommunity: false,
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

function given(
  role: "ORGANIZER" | "MEMBER" | null,
  policy = closedPolicy,
  isLoading = false,
) {
  vi.mocked(useMyMembership).mockReturnValue({
    data: role === null ? null : { role },
    isLoading,
  } as never);
  vi.mocked(useManagementPolicyQuery).mockReturnValue({
    data: policy,
    isLoading,
  } as never);
}

describe("useManagementAccess", () => {
  it("grants an organizer everything", () => {
    // given
    given("ORGANIZER");

    // when
    const { result } = renderHook(() => useManagementAccess("backend-guild"));

    // then
    expect(result.current).toEqual({
      editCommunity: true,
      lookAndFeel: true,
      members: true,
      contributionPolicy: true,
      archive: true,
      managementPolicy: true,
      curation: true,
      health: true,
      invite: true,
      communication: true,
      credit: true,
      any: true,
      isLoading: false,
    });
  });

  it("grants a member only the axes the policy opens", () => {
    // given
    given("MEMBER", { ...closedPolicy, canManageLookAndFeel: true });

    // when
    const { result } = renderHook(() => useManagementAccess("backend-guild"));

    // then
    expect(result.current.lookAndFeel).toBe(true);
    expect(result.current.archive).toBe(false);
    expect(result.current.managementPolicy).toBe(false);
  });

  it("grants a non-member nothing", () => {
    // given - the membership query resolved to null
    given(null, { ...closedPolicy, canManageLookAndFeel: true });

    // when
    const { result } = renderHook(() => useManagementAccess("backend-guild"));

    // then
    expect(result.current.any).toBe(false);
  });

  it("grants a member nothing while the policy is unknown", () => {
    // given - the policy request failed or has not answered
    given("MEMBER");
    vi.mocked(useManagementPolicyQuery).mockReturnValue({
      data: undefined,
      isLoading: false,
    } as never);

    // when
    const { result } = renderHook(() => useManagementAccess("backend-guild"));

    // then
    expect(result.current.any).toBe(false);
  });

  const closedAccess: Omit<ManagementAccess, "any"> = {
    editCommunity: false,
    lookAndFeel: false,
    members: false,
    contributionPolicy: false,
    archive: false,
    managementPolicy: false,
    curation: false,
    health: false,
    invite: false,
    communication: false,
    credit: false,
    isLoading: false,
  };

  // Each row names one policy axis and the single access flag it must, and
  // must only, open for a MEMBER. This pins every axis of the mapping table
  // individually, so a transposed pair (e.g. curation wired to
  // canViewHealth) fails here instead of slipping through untested.
  const policyGatedAxes: ReadonlyArray<{
    policyKey: keyof typeof closedPolicy;
    accessKey: keyof typeof closedAccess;
  }> = [
    { policyKey: "canEditCommunity", accessKey: "editCommunity" },
    { policyKey: "canManageLookAndFeel", accessKey: "lookAndFeel" },
    { policyKey: "canManageMembers", accessKey: "members" },
    {
      policyKey: "canManageContributionPolicy",
      accessKey: "contributionPolicy",
    },
    { policyKey: "canArchiveCommunity", accessKey: "archive" },
    { policyKey: "canCurateTags", accessKey: "curation" },
    { policyKey: "canViewHealth", accessKey: "health" },
    { policyKey: "canInviteMembers", accessKey: "invite" },
    { policyKey: "canManageCommunication", accessKey: "communication" },
    { policyKey: "canCreditContributions", accessKey: "credit" },
  ];

  it.each(policyGatedAxes)(
    "opens only $accessKey for a member when $policyKey is the sole open policy axis",
    ({ policyKey, accessKey }) => {
      // given - a member with exactly one policy axis switched on
      given("MEMBER", { ...closedPolicy, [policyKey]: true });

      // when
      const { result } = renderHook(() => useManagementAccess("backend-guild"));

      // then - the matching flag opened, every other policy-gated flag and
      // the organizer-only managementPolicy flag all stayed shut
      expect(result.current).toEqual({
        ...closedAccess,
        [accessKey]: true,
        any: true,
      });
    },
  );

  it("reports loading while membership or policy has not resolved", () => {
    // given
    given("MEMBER", closedPolicy, true);

    // when
    const { result } = renderHook(() => useManagementAccess("backend-guild"));

    // then
    expect(result.current.isLoading).toBe(true);
  });
});
