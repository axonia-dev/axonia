import { fireEvent, render, screen, within } from "@testing-library/react";
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

const access = {
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

beforeEach(() => {
  user = userEvent.setup({ delay: null });
  vi.clearAllMocks();
  vi.mocked(useManagementAccess).mockReturnValue(access);
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

describe("CommunityPolicyPage open-section counters", () => {
  it("tracks how many sections are open, in the header and per group", async () => {
    // when
    renderPage();
    const cadence = within(
      screen.getByRole("region", { name: "Cadence & events" }),
    );

    // then — nothing open yet
    expect(screen.getByText("0", { selector: "b" })).toBeInTheDocument();
    expect(cadence.getByText(/0 \/ 4 open/)).toBeInTheDocument();

    // when one cadence axis opens
    await user.click(screen.getByRole("switch", { name: /start a series/i }));

    // then both the header tally and the group counter move
    expect(screen.getByText("1", { selector: "b" })).toBeInTheDocument();
    expect(cadence.getByText(/1 \/ 4 open/)).toBeInTheDocument();
  });

  it("counts four closed axes in People & knowledge by default", () => {
    // when
    renderPage();

    // then
    const people = within(
      screen.getByRole("region", { name: "People & knowledge" }),
    );
    expect(people.getByText("0 / 4 open")).toBeInTheDocument();
  });

  it("pulses a switch open then settles it, and toggles back to closed", async () => {
    // given
    renderPage();
    const sw = screen.getByRole("switch", { name: /start a series/i });

    // when opened, the glow pulse runs and then its animation ends
    await user.click(sw);
    const track = sw.querySelector("span");
    if (track != null) fireEvent.animationEnd(track);

    // and when closed again
    await user.click(sw);

    // then the tally is back to zero
    expect(screen.getByText("0", { selector: "b" })).toBeInTheDocument();
  });

  it("opens a danger axis with its own counter", async () => {
    // when
    renderPage();
    await user.click(
      screen.getByRole("switch", { name: /archive the community/i }),
    );

    // then the danger group reports one of its two axes open
    expect(screen.getByText(/1 \/ 2 open/)).toBeInTheDocument();
  });
});
