import { render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter, Route, Routes } from "react-router-dom";
import { beforeEach, describe, expect, it, vi } from "vitest";

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

// Defaults enterprise; see CommunityPolicyPage.hub.test.tsx for the HUB row.
vi.mock("@/features/admin/api/use-current-organization", () => ({
  useCurrentOrganization: vi.fn(() => ({ data: { kind: "ENTERPRISE" } })),
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

let user: ReturnType<typeof userEvent.setup>;
const managementMutateAsync = vi.fn();

beforeEach(() => {
  user = userEvent.setup({ delay: null });
  vi.clearAllMocks();
  managementMutateAsync.mockResolvedValue(undefined);
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
    mutateAsync: managementMutateAsync,
  } as never);
});

describe("CommunityPolicyPage Credit contributions axis", () => {
  it("offers Credit contributions among people & knowledge, and saves it with every axis", async () => {
    // given
    renderPage();
    const people = within(
      screen.getByRole("region", { name: "People & knowledge" }),
    );

    // when
    await user.click(
      people.getByRole("switch", { name: /credit contributions/i }),
    );

    // then
    await waitFor(() =>
      expect(managementMutateAsync).toHaveBeenCalledWith({
        ...MANAGEMENT,
        canCreditContributions: true,
      }),
    );
  });
});
