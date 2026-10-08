import { render, screen } from "@testing-library/react";
import { MemoryRouter, Route, Routes } from "react-router-dom";
import { beforeEach, describe, expect, it, vi } from "vitest";

import { useCurrentOrganization } from "@/features/admin/api/use-current-organization";
import {
  useChatDestinationsQuery,
  useCreateChatDestination,
  useRemoveChatDestination,
  useTestChatDestination,
  useUpdateChatDestination,
} from "@/features/community/api/use-chat-destinations";
import {
  useCommunicationSettingsQuery,
  useUpdateCommunicationSettings,
} from "@/features/community/api/use-communication-settings";
import type { ManagementAccess } from "@/features/community/api/use-management-access";
import { useManagementAccess } from "@/features/community/api/use-management-access";
import { CommunicationSettingsPage } from "@/features/community/ui/CommunicationSettingsPage";

vi.mock("@/features/admin/api/use-current-organization", () => ({
  useCurrentOrganization: vi.fn(),
}));
vi.mock("@/features/community/api/use-management-access", () => ({
  useManagementAccess: vi.fn(),
}));
vi.mock("@/features/community/api/use-communication-settings", () => ({
  useCommunicationSettingsQuery: vi.fn(),
  useUpdateCommunicationSettings: vi.fn(),
}));
vi.mock("@/features/community/api/use-chat-destinations", () => ({
  useChatDestinationsQuery: vi.fn(),
  useCreateChatDestination: vi.fn(),
  useUpdateChatDestination: vi.fn(),
  useRemoveChatDestination: vi.fn(),
  useTestChatDestination: vi.fn(),
}));
vi.mock("@/features/community/ui/community-chrome", () => ({
  useCommunityCrumbs: vi.fn(),
}));

const SETTINGS = {
  notifySessionUpcoming: true,
  notifySessionWrapUp: true,
  sessionUpcomingLeadDays: 3,
  wrapUpGraceHours: 48,
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

function mockSettings(overrides?: Partial<typeof SETTINGS>) {
  vi.mocked(useCommunicationSettingsQuery).mockReturnValue({
    data: { ...SETTINGS, ...overrides },
    isPending: false,
  } as never);
}

function renderPage(overrides?: Partial<typeof SETTINGS>) {
  mockSettings(overrides);
  render(
    <MemoryRouter
      initialEntries={["/communities/backend-guild/settings/communication"]}
    >
      <Routes>
        <Route
          path="/communities/:slug/settings/communication"
          element={<CommunicationSettingsPage />}
        />
      </Routes>
    </MemoryRouter>,
  );
}

beforeEach(() => {
  vi.clearAllMocks();
  vi.mocked(useManagementAccess).mockReturnValue(access());
  vi.mocked(useCurrentOrganization).mockReturnValue({
    data: {
      id: "acme",
      name: "Acme",
      kind: "ENTERPRISE",
      communicationsEnabled: true,
    },
  } as never);
  vi.mocked(useUpdateCommunicationSettings).mockReturnValue({
    mutate: vi.fn(),
    isPending: false,
    isError: false,
    error: null,
  } as never);
  vi.mocked(useChatDestinationsQuery).mockReturnValue({
    data: [],
    isPending: false,
    isError: false,
  } as never);
  vi.mocked(useCreateChatDestination).mockReturnValue({
    mutate: vi.fn(),
    isPending: false,
    isError: false,
    error: null,
  } as never);
  vi.mocked(useUpdateChatDestination).mockReturnValue({
    mutate: vi.fn(),
    isPending: false,
    isError: false,
    error: null,
  } as never);
  vi.mocked(useRemoveChatDestination).mockReturnValue({
    mutate: vi.fn(),
    isPending: false,
    isError: false,
    error: null,
  } as never);
  vi.mocked(useTestChatDestination).mockReturnValue({
    mutate: vi.fn(),
    isPending: false,
  } as never);
});

describe("CommunicationSettingsPage", () => {
  it("titles the page without claiming it is only about email", () => {
    renderPage();

    expect(
      screen.getByRole("heading", { name: "Communication", level: 1 }),
    ).toBeVisible();
    expect(
      screen.getByText("What this community sends, and where it goes."),
    ).toBeVisible();
    expect(screen.queryByText(/emails its members/i)).toBeNull();
  });

  it("has exactly one Save", async () => {
    renderPage();

    expect(await screen.findAllByRole("button", { name: "Save" })).toHaveLength(
      1,
    );
  });

  it("carries neither the old webhook helper text nor the consent paragraph", () => {
    renderPage();

    expect(screen.queryByText(/Paste an incoming webhook URL/)).toBeNull();
    expect(screen.queryByText(/whether or not they are a member/)).toBeNull();
  });

  it("tells a member with no delegated access they lack access, without implying organizer-only", () => {
    // given - the axis is delegable, so "Organizers only" would be inaccurate:
    // an organizer could have granted this to a member
    vi.mocked(useManagementAccess).mockReturnValue(
      access({ communication: false }),
    );

    // when
    renderPage();

    // then - the page title still shows, and the denial doesn't say "organizers only"
    expect(
      screen.getByRole("heading", { name: "Communication" }),
    ).toBeInTheDocument();
    expect(screen.queryByText(/organizers only/i)).not.toBeInTheDocument();
    expect(screen.getByText(/no access/i)).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "Save" })).toBeNull();
  });

  it("shows a loading hint while settings load, with the page title already visible", () => {
    // given
    vi.mocked(useCommunicationSettingsQuery).mockReturnValue({
      data: undefined,
      isPending: true,
    } as never);

    // when
    render(
      <MemoryRouter
        initialEntries={["/communities/backend-guild/settings/communication"]}
      >
        <Routes>
          <Route
            path="/communities/:slug/settings/communication"
            element={<CommunicationSettingsPage />}
          />
        </Routes>
      </MemoryRouter>,
    );

    // then - an organizer's first paint is never a bare, unstyled sentence
    expect(
      screen.getByRole("heading", { name: "Communication" }),
    ).toBeInTheDocument();
    expect(screen.getByText(/loading/i)).toBeInTheDocument();
  });
});
