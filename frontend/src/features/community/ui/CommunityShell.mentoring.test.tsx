import { render, screen, within } from "@testing-library/react";
import { Fragment } from "react";
import { MemoryRouter, Route, Routes } from "react-router-dom";
import { describe, expect, it, vi } from "vitest";

import { DelegatedSessionProvider } from "@/auth/delegated-session";
import { SidebarProvider } from "@/components/patterns/SidebarProvider";
import { CommunityShell } from "@/features/community/ui/CommunityShell";
import { ThemeProvider } from "@/theme/ThemeProvider";

// The Mentoring rail entry, shown only for a community that switched mentoring on.

const mentoring = vi.hoisted(() => ({
  enabled: false,
  member: true,
}));

vi.mock("@/features/community/api/use-communities", () => ({
  useCommunity: vi.fn(() => ({
    isLoading: false,
    isError: false,
    data: {
      id: 1,
      slug: "backend-guild",
      name: "Backend Guild",
      kindId: 1,
      memberCount: 120,
      mentoringEnabled: mentoring.enabled,
    },
  })),
  useCommunities: () => ({ data: { content: [] } }),
}));

vi.mock("@/features/admin/api/use-current-organization", () => ({
  useCurrentOrganization: vi.fn(() => ({ data: undefined })),
}));

vi.mock("@/features/admin/api/use-permissions", () => ({
  usePermissions: vi.fn(() => ({ can: () => false, isLoading: false })),
}));

// No QueryClientProvider here, so the bell and onboarding queries are stubbed.
vi.mock("@/features/communication/api/use-unread-count", () => ({
  useUnreadCount: () => ({ data: 0 }),
}));

vi.mock("@/features/onboarding/api/use-onboarding", () => ({
  useOnboarding: () => ({ data: undefined }),
  useRecordVisit: () => ({ mutate: vi.fn() }),
  useAssignOnboarding: () => ({ mutate: vi.fn() }),
  useCloseOnboarding: () => ({ mutate: vi.fn() }),
}));

vi.mock("@/features/community/api/use-management-access", () => ({
  useManagementAccess: vi.fn(() => ({
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
    any: false,
    member: mentoring.member,
    isLoading: false,
  })),
}));

// AccountMenu (rendered inside the shell) needs auth + /me.
vi.mock("@/auth/session", () => ({
  useAuth: () => ({
    user: {
      profile: {
        preferred_username: "alice",
        sub: "sub-1",
        email: "alice@example.com",
      },
    },
    signoutRedirect: vi.fn(),
  }),
}));

vi.mock("@/features/identity/api/use-me", () => ({
  useMe: () => ({
    data: {
      id: "sub-1",
      displayName: "Alice Anderson",
      email: "alice@example.com",
    },
  }),
}));

function renderShell(location: string, delegated = false) {
  const Session = delegated ? DelegatedSessionProvider : Fragment;
  render(
    <ThemeProvider>
      <Session>
        <SidebarProvider>
          <MemoryRouter initialEntries={[location]}>
            <Routes>
              <Route path="/communities/:slug" element={<CommunityShell />}>
                <Route index element={<p>overview-panel</p>} />
                <Route path="mentoring" element={<p>mentoring-panel</p>} />
                <Route path="me/mentoring/:id" element={<p>journal-panel</p>} />
              </Route>
            </Routes>
          </MemoryRouter>
        </SidebarProvider>
      </Session>
    </ThemeProvider>,
  );
}

describe("CommunityShell Mentoring entry", () => {
  it("hides Mentoring from the rail when mentoring is off", () => {
    // given
    mentoring.enabled = false;

    // when
    renderShell("/communities/backend-guild");

    // then
    const rail = within(screen.getByTestId("community-rail"));
    expect(
      rail.queryByRole("link", { name: "Mentoring" }),
    ).not.toBeInTheDocument();
  });

  it("lists Mentoring after Learning when it is on", () => {
    // given
    mentoring.enabled = true;
    mentoring.member = true;

    // when
    renderShell("/communities/backend-guild/mentoring");

    // then
    const rail = within(screen.getByTestId("community-rail"));
    const links = rail
      .getAllByRole("link")
      .map((link) => link.textContent.trim());
    expect(links.indexOf("Mentoring")).toBe(links.indexOf("Learning") + 1);
    expect(rail.getByRole("link", { name: "Mentoring" })).toHaveAttribute(
      "href",
      "/communities/backend-guild/mentoring",
    );
    expect(rail.getByRole("link", { name: "Mentoring" })).toHaveAttribute(
      "aria-current",
      "page",
    );
  });

  it("hides Mentoring from a non-member even when it is on", () => {
    // given
    mentoring.enabled = true;
    mentoring.member = false;

    // when
    renderShell("/communities/backend-guild");

    // then
    const rail = within(screen.getByTestId("community-rail"));
    expect(
      rail.queryByRole("link", { name: "Mentoring" }),
    ).not.toBeInTheDocument();
    mentoring.member = true;
  });

  it("hides Mentoring from a delegated session even for a member", () => {
    // given
    mentoring.enabled = true;
    mentoring.member = true;

    // when
    renderShell("/communities/backend-guild", true);

    // then
    const rail = within(screen.getByTestId("community-rail"));
    expect(
      rail.queryByRole("link", { name: "Mentoring" }),
    ).not.toBeInTheDocument();
  });

  it("hides My mentoring where mentoring is off", () => {
    // given
    mentoring.enabled = false;

    // when
    renderShell("/communities/backend-guild");

    // then
    const rail = within(screen.getByTestId("community-rail"));
    expect(
      rail.queryByRole("link", { name: "My mentoring" }),
    ).not.toBeInTheDocument();
  });

  it("lists My mentoring after My expertise and keeps it lit on a journal", () => {
    // given
    mentoring.enabled = true;
    mentoring.member = true;

    // when
    renderShell("/communities/backend-guild/me/mentoring/11");

    // then
    const rail = within(screen.getByTestId("community-rail"));
    const links = rail
      .getAllByRole("link")
      .map((link) => link.textContent.trim());
    expect(links.indexOf("My mentoring")).toBe(
      links.indexOf("My expertise") + 1,
    );
    expect(rail.getByRole("link", { name: "My mentoring" })).toHaveAttribute(
      "aria-current",
      "page",
    );
  });
});
