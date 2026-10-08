import { describe, expect, it } from "vitest";

import {
  curated,
  managementPolicySchema,
} from "@/features/community/domain/management-policy";

describe("managementPolicySchema", () => {
  it("parses a valid policy", () => {
    // given
    const raw = {
      canEditCommunity: true,
      canManageLookAndFeel: false,
      canManageMembers: true,
      canManageContributionPolicy: false,
      canArchiveCommunity: true,
      canCurateTags: false,
      canViewHealth: true,
      canInviteMembers: true,
      canManageCommunication: false,
      canCreditContributions: false,
    };

    // when
    const actual = managementPolicySchema.parse(raw);

    // then
    expect(actual).toEqual(raw);
  });

  it("rejects a payload with a missing axis", () => {
    // given
    const raw = { canEditCommunity: true, canManageLookAndFeel: false };

    // when
    const actual = managementPolicySchema.safeParse(raw);

    // then
    expect(actual.success).toBe(false);
  });
});

describe("curated", () => {
  it("locks every axis", () => {
    // when
    const actual = curated();

    // then
    expect(actual).toEqual({
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
    });
  });
});
