import { render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter, Route, Routes } from "react-router-dom";
import { beforeEach, describe, expect, it, vi } from "vitest";

import { notifyError } from "@/components/ui/toast";
import {
  useContributionPolicyQuery,
  useUpdateContributionPolicy,
} from "@/features/community/api/use-contribution-policy";
import type { ManagementAccess } from "@/features/community/api/use-management-access";
import { useManagementAccess } from "@/features/community/api/use-management-access";
import {
  useManagementPolicyQuery,
  useUpdateManagementPolicy,
} from "@/features/community/api/use-management-policy";
import { CommunityPolicyPage } from "@/features/community/ui/CommunityPolicyPage";

vi.mock("@/components/ui/toast", () => ({
  notifyError: vi.fn(),
}));
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

function access(overrides?: Partial<ManagementAccess>): ManagementAccess {
  return {
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
    ...overrides,
  };
}

const notifyErrorMock = vi.mocked(notifyError);
const contributionMutateAsync = vi.fn();
const managementMutateAsync = vi.fn();

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
  contributionMutateAsync.mockResolvedValue(undefined);
  managementMutateAsync.mockResolvedValue(undefined);
  vi.mocked(useManagementAccess).mockReturnValue(access());
  vi.mocked(useContributionPolicyQuery).mockReturnValue({
    data: CONTRIBUTION,
    isPending: false,
  } as never);
  vi.mocked(useManagementPolicyQuery).mockReturnValue({
    data: MANAGEMENT,
    isPending: false,
  } as never);
  vi.mocked(useUpdateContributionPolicy).mockReturnValue({
    mutateAsync: contributionMutateAsync,
  } as never);
  vi.mocked(useUpdateManagementPolicy).mockReturnValue({
    mutateAsync: managementMutateAsync,
  } as never);
});

describe("CommunityPolicyPage", () => {
  it("shows every area group to an organizer, with no Save button", () => {
    // when
    renderPage();

    // then - one group per area, including the danger zone
    expect(screen.getByText("Cadence & events")).toBeInTheDocument();
    expect(screen.getByText("Identity & looks")).toBeInTheDocument();
    expect(screen.getByText("People & knowledge")).toBeInTheDocument();
    expect(screen.getByText("Communication")).toBeInTheDocument();
    expect(screen.getByText("Danger zone")).toBeInTheDocument();
    expect(
      screen.getByRole("switch", { name: /start a series/i }),
    ).not.toBeChecked();
    expect(
      screen.getByRole("switch", { name: /archive the community/i }),
    ).not.toBeChecked();
    expect(screen.queryByRole("button", { name: /save/i })).toBeNull();
    expect(
      screen.getByRole("switch", { name: /curate the expertise taxonomy/i }),
    ).toBeInTheDocument();
    expect(
      screen.getByRole("switch", { name: /view community health/i }),
    ).toBeInTheDocument();
  });

  // Communication neither escalates nor destroys - the worst it can do is stop session mail,
  // which is one switch back - so it does not belong beside "archive the community". It reached
  // the danger zone as a hotfix for a failing save, never as a decision.
  it("offers the communication axis in its own group rather than the danger zone", () => {
    const axis = { name: /manage communication/i } as const;
    renderPage();
    const group = (name: string) =>
      within(screen.getByRole("region", { name }));

    expect(
      group("Communication").getByRole("switch", axis),
    ).toBeInTheDocument();
    expect(group("Danger zone").queryByRole("switch", axis)).toBeNull();
  });

  it("auto-saves the management policy with every axis, including communication, when one flips", async () => {
    // when an organizer flips the new communication axis
    renderPage();
    await user.click(
      screen.getByRole("switch", { name: /manage communication/i }),
    );

    // then the write carries every axis, not just the flipped one - the shape
    // the backend's @NotNull Boolean requires
    await waitFor(() =>
      expect(managementMutateAsync).toHaveBeenCalledWith({
        ...MANAGEMENT,
        canManageCommunication: true,
      }),
    );
  });

  it("auto-saves the contribution policy the moment a cadence switch flips, leaving the management policy untouched", async () => {
    // when an organizer flips a cadence axis
    renderPage();
    await user.click(screen.getByRole("switch", { name: /start a series/i }));

    // then it persists on its own - no button, no success label
    await waitFor(() =>
      expect(contributionMutateAsync).toHaveBeenCalledWith({
        ...CONTRIBUTION,
        canStartSeries: true,
      }),
    );
    expect(managementMutateAsync).not.toHaveBeenCalled();
    expect(screen.queryByText(/saved/i)).toBeNull();
  });

  it("auto-saves the management policy the moment an identity switch flips", async () => {
    // when an organizer flips a management axis
    renderPage();
    await user.click(
      screen.getByRole("switch", { name: /edit the community/i }),
    );

    // then only the management policy is written
    await waitFor(() =>
      expect(managementMutateAsync).toHaveBeenCalledWith({
        ...MANAGEMENT,
        canEditCommunity: true,
      }),
    );
    expect(contributionMutateAsync).not.toHaveBeenCalled();
  });

  it("never lets two auto-saves overlap on the same community row, even across policies", async () => {
    // given a contribution save we hold open
    let releaseContribution = () => {};
    contributionMutateAsync.mockReturnValue(
      new Promise<void>((resolve) => {
        releaseContribution = () => resolve();
      }),
    );

    // when an organizer flips a cadence axis and then a management axis
    renderPage();
    await user.click(screen.getByRole("switch", { name: /start a series/i }));
    await user.click(
      screen.getByRole("switch", { name: /edit the community/i }),
    );

    // then the management write waits while the contribution write is in flight
    expect(contributionMutateAsync).toHaveBeenCalledTimes(1);
    expect(managementMutateAsync).not.toHaveBeenCalled();

    // and only follows once the contribution write resolves
    releaseContribution();
    await waitFor(() => expect(managementMutateAsync).toHaveBeenCalledTimes(1));
  });

  it("snaps the switch back and raises a toast when a save fails", async () => {
    // given the contribution save will fail
    contributionMutateAsync.mockRejectedValue(new Error("boom"));

    // when an organizer flips a cadence axis
    renderPage();
    const sw = screen.getByRole("switch", { name: /start a series/i });
    await user.click(sw);

    // then a toast is raised and the switch reverts to its last-saved state
    await waitFor(() => expect(notifyErrorMock).toHaveBeenCalled());
    expect(sw).not.toBeChecked();
  });

  it("shows a delegated member only the cadence group and auto-saves only that policy", async () => {
    // given a member who may edit the contribution policy but not manage
    vi.mocked(useManagementAccess).mockReturnValue(
      access({ managementPolicy: false }),
    );

    // when
    renderPage();
    await user.click(screen.getByRole("switch", { name: /start a series/i }));

    // then - no management group is shown, and only the contribution policy saves
    expect(screen.getByText("Cadence & events")).toBeInTheDocument();
    expect(screen.queryByText("Danger zone")).toBeNull();
    expect(
      screen.queryByRole("switch", { name: /archive the community/i }),
    ).toBeNull();
    await waitFor(() =>
      expect(contributionMutateAsync).toHaveBeenCalledWith({
        ...CONTRIBUTION,
        canStartSeries: true,
      }),
    );
    expect(managementMutateAsync).not.toHaveBeenCalled();
  });

  it("tells a user with no policy access that it is organizers only", () => {
    vi.mocked(useManagementAccess).mockReturnValue(
      access({ contributionPolicy: false, managementPolicy: false }),
    );
    renderPage();
    expect(screen.getByText(/organizers only/i)).toBeInTheDocument();
    expect(screen.queryByRole("switch")).toBeNull();
  });

  it("shows a loading hint while a policy loads", () => {
    vi.mocked(useContributionPolicyQuery).mockReturnValue({
      data: undefined,
      isPending: true,
    } as never);
    renderPage();
    expect(screen.getByText(/loading/i)).toBeInTheDocument();
  });
});
