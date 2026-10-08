import { render, screen } from "@testing-library/react";
import { MemoryRouter, Route, Routes } from "react-router-dom";
import { beforeEach, describe, expect, it, vi } from "vitest";

import { useCurrentOrganization } from "@/features/admin/api/use-current-organization";
import {
  useContributionPolicyQuery,
  useUpdateContributionPolicy,
} from "@/features/community/api/use-contribution-policy";
import { useManagementAccess } from "@/features/community/api/use-management-access";
import {
  useManagementPolicyQuery,
  useUpdateManagementPolicy,
} from "@/features/community/api/use-management-policy";
import { CommunityPolicyPage } from "@/features/community/ui/CommunityPolicyPage";

vi.mock("@/features/admin/api/use-current-organization", () => ({
  useCurrentOrganization: vi.fn(),
}));
vi.mock("@/features/community/api/use-management-access", () => ({
  useManagementAccess: vi.fn(),
}));
vi.mock("@/features/community/api/use-contribution-policy", () => ({
  useContributionPolicyQuery: vi.fn(),
  useUpdateContributionPolicy: vi.fn(),
}));
vi.mock("@/features/community/api/use-management-policy", () => ({
  useManagementPolicyQuery: vi.fn(),
  useUpdateManagementPolicy: vi.fn(),
}));
vi.mock("@/features/community/ui/community-chrome", () => ({
  useCommunityCrumbs: vi.fn(),
}));

const CONTRIBUTION = {
  canStartSeries: false,
  canEditStructure: false,
  canQueueTopics: false,
  canManageSchedule: false,
};

const MANAGEMENT = {
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

// An organizer: every access flag open, so the whole management policy renders.
const organizerAccess = {
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
};

function renderPage() {
  render(
    <MemoryRouter
      initialEntries={["/communities/backend-guild/settings/policy"]}
    >
      <Routes>
        <Route
          path="/communities/:slug/settings/policy"
          element={<CommunityPolicyPage />}
        />
      </Routes>
    </MemoryRouter>,
  );
}

describe("CommunityPolicyPage Invite people row", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    vi.mocked(useManagementAccess).mockReturnValue(organizerAccess);
    vi.mocked(useContributionPolicyQuery).mockReturnValue({
      data: CONTRIBUTION,
      isPending: false,
    } as never);
    vi.mocked(useManagementPolicyQuery).mockReturnValue({
      data: MANAGEMENT,
      isPending: false,
    } as never);
    vi.mocked(useUpdateContributionPolicy).mockReturnValue({
      mutateAsync: vi.fn().mockResolvedValue(undefined),
    } as never);
    vi.mocked(useUpdateManagementPolicy).mockReturnValue({
      mutateAsync: vi.fn().mockResolvedValue(undefined),
    } as never);
  });

  it("shows the Invite people switch in a self-serve community", () => {
    // given
    vi.mocked(useCurrentOrganization).mockReturnValue({
      data: { id: "rust-hub", kind: "HUB" },
    } as ReturnType<typeof useCurrentOrganization>);

    // when — render as an organizer
    renderPage();

    // then
    expect(screen.getByText("Invite people")).toBeInTheDocument();
  });

  it("never shows the Invite people switch in an enterprise organization", () => {
    // given
    vi.mocked(useCurrentOrganization).mockReturnValue({
      data: { id: "acme", kind: "ENTERPRISE" },
    } as ReturnType<typeof useCurrentOrganization>);

    // when — render as an organizer
    renderPage();

    // then
    expect(screen.queryByText("Invite people")).not.toBeInTheDocument();
  });
});
