import { render, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter, Route, Routes } from "react-router-dom";
import { describe, expect, it, vi } from "vitest";

import { SidebarProvider } from "@/components/patterns/SidebarProvider";
import { useManagementAccess } from "@/features/community/api/use-management-access";
import { CommunityShell } from "@/features/community/ui/CommunityShell";
import { ThemeProvider } from "@/theme/ThemeProvider";

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
    },
  })),
  useCommunities: () => ({ data: { content: [] } }),
}));

// CommunityShell now also reads the current organization + permissions to
// decide whether to show the hub-only rail groups (see
// CommunityShell.hub.test.tsx); default to a pending/enterprise-like org here
// so these plain-shell tests are unaffected.
vi.mock("@/features/admin/api/use-current-organization", () => ({
  useCurrentOrganization: vi.fn(() => ({ data: undefined })),
}));

vi.mock("@/features/admin/api/use-permissions", () => ({
  usePermissions: vi.fn(() => ({ can: () => false, isLoading: false })),
}));

// The notification bell in both top bars; no QueryClientProvider here, so its query is stubbed.
vi.mock("@/features/communication/api/use-unread-count", () => ({
  useUnreadCount: () => ({ data: 4 }),
}));

// The onboarding affordance in both top bars; no QueryClientProvider here, so its query is stubbed.
vi.mock("@/features/onboarding/api/use-onboarding", () => ({
  useOnboarding: () => ({ data: undefined }),
  useRecordVisit: () => ({ mutate: vi.fn() }),
  useAssignOnboarding: () => ({ mutate: vi.fn() }),
  useCloseOnboarding: () => ({ mutate: vi.fn() }),
}));

const noAccess = {
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
  isLoading: false,
};

vi.mock("@/features/community/api/use-management-access", () => ({
  useManagementAccess: vi.fn(() => noAccess),
}));

// AccountMenu (now rendered inside the shell) needs auth + /me.
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

// Registers both destinations regardless of which the caller visits: cheaper than
// building a one-off tree per test, and a route neither the location nor an
// assertion reaches has no effect on the other.
function renderShell(location = "/communities/backend-guild") {
  render(
    <ThemeProvider>
      <SidebarProvider>
        <MemoryRouter initialEntries={[location]}>
          <Routes>
            <Route path="/communities/:slug" element={<CommunityShell />}>
              <Route index element={<p>overview-panel</p>} />
              <Route
                path="expertise/curation"
                element={<p>curation-panel</p>}
              />
            </Route>
          </Routes>
        </MemoryRouter>
      </SidebarProvider>
    </ThemeProvider>,
  );
}

describe("CommunityShell", () => {
  it("renders the switcher, desktop section rail, breadcrumb, and routed section", () => {
    renderShell();
    const rail = within(screen.getByTestId("community-rail"));
    expect(
      rail.getByRole("button", { name: /switch community/i }),
    ).toBeInTheDocument();
    expect(rail.getByRole("link", { name: "Schedule" })).toHaveAttribute(
      "href",
      "/communities/backend-guild/schedule",
    );
    expect(rail.queryByRole("link", { name: "Upcoming" })).toBeNull();
    expect(screen.getByRole("link", { name: "Communities" })).toHaveAttribute(
      "href",
      "/communities",
    );
    expect(screen.getByText("overview-panel")).toBeInTheDocument();
  });

  it("shows the account menu in the desktop top bar, not the rail", async () => {
    const user = userEvent.setup({ delay: null });
    renderShell();
    const rail = within(screen.getByTestId("community-rail"));
    expect(
      rail.queryByRole("button", { name: "Account" }),
    ).not.toBeInTheDocument();
    const topbar = within(screen.getByTestId("community-desktop-topbar"));
    await user.click(topbar.getByRole("button", { name: "Account" }));
    expect(screen.getByText("Alice Anderson")).toBeInTheDocument();
  });

  // A badge nobody sees does nothing: the bell belongs in every shell, not only on the list.
  it("carries the notification bell in both top bars", () => {
    renderShell();
    const bell = { name: "Notifications, 4 unread" };
    for (const bar of ["community-desktop-topbar", "community-topbar"]) {
      const found = within(screen.getByTestId(bar)).getByRole("button", bell);
      expect(found).toBeInTheDocument();
    }
  });

  it("offers a bottom tab bar with Overview and category buttons", () => {
    renderShell();
    const tabs = within(screen.getByTestId("community-tabs"));
    expect(tabs.getByRole("link", { name: "Overview" })).toHaveAttribute(
      "href",
      "/communities/backend-guild",
    );
    expect(tabs.getByRole("button", { name: "Engage" })).toBeInTheDocument();
    expect(tabs.getByRole("button", { name: "Knowledge" })).toBeInTheDocument();
  });

  it("adds a Settings tab and drops the theme toggle from both headers", () => {
    renderShell();
    const tabs = within(screen.getByTestId("community-tabs"));
    expect(tabs.getByRole("link", { name: "Settings" })).toHaveAttribute(
      "href",
      "/communities/backend-guild/settings/account",
    );
    const rail = within(screen.getByTestId("community-rail"));
    expect(rail.queryByRole("button", { name: /theme/i })).toBeNull();
    expect(tabs.queryByRole("button", { name: /theme/i })).toBeNull();
  });

  it("opens a section sheet when a category button is tapped", async () => {
    const user = userEvent.setup({ delay: null });
    renderShell();
    const tabs = within(screen.getByTestId("community-tabs"));
    await user.click(tabs.getByRole("button", { name: "Engage" }));
    const sheet = within(await screen.findByRole("dialog"));
    expect(sheet.getByRole("link", { name: "Schedule" })).toHaveAttribute(
      "href",
      "/communities/backend-guild/schedule",
    );
    expect(sheet.getByRole("link", { name: "Backlog" })).toBeInTheDocument();
  });

  it("shows a Manage category to organizers (both items) in the bottom bar", () => {
    vi.mocked(useManagementAccess).mockReturnValueOnce({
      ...noAccess,
      curation: true,
      health: true,
      any: true,
    });
    renderShell();
    const tabs = within(screen.getByTestId("community-tabs"));
    expect(tabs.getByRole("button", { name: "Manage" })).toBeInTheDocument();
  });

  it("hides the Manage group from plain members (no access)", () => {
    renderShell();
    const rail = within(screen.getByTestId("community-rail"));
    expect(
      rail.queryByRole("link", { name: "Health" }),
    ).not.toBeInTheDocument();
    expect(
      rail.queryByRole("link", { name: "Curation" }),
    ).not.toBeInTheDocument();
  });

  it("shows only Curation when member has curation access but not health", () => {
    vi.mocked(useManagementAccess).mockReturnValueOnce({
      ...noAccess,
      curation: true,
      any: true,
    });
    renderShell();
    const rail = within(screen.getByTestId("community-rail"));
    expect(rail.getByRole("link", { name: "Curation" })).toHaveAttribute(
      "href",
      "/communities/backend-guild/expertise/curation",
    );
    expect(
      rail.queryByRole("link", { name: "Health" }),
    ).not.toBeInTheDocument();
  });

  it("shows only Health when member has health access but not curation", () => {
    vi.mocked(useManagementAccess).mockReturnValueOnce({
      ...noAccess,
      health: true,
      any: true,
    });
    renderShell();
    const rail = within(screen.getByTestId("community-rail"));
    expect(rail.getByRole("link", { name: "Health" })).toHaveAttribute(
      "href",
      "/communities/backend-guild/health",
    );
    expect(
      rail.queryByRole("link", { name: "Curation" }),
    ).not.toBeInTheDocument();
  });

  it("shows both Health and Curation to organizers", () => {
    vi.mocked(useManagementAccess).mockReturnValueOnce({
      ...noAccess,
      curation: true,
      health: true,
      any: true,
    });
    renderShell();
    const rail = within(screen.getByTestId("community-rail"));
    expect(rail.getByRole("link", { name: "Health" })).toHaveAttribute(
      "href",
      "/communities/backend-guild/health",
    );
    expect(rail.getByRole("link", { name: "Curation" })).toHaveAttribute(
      "href",
      "/communities/backend-guild/expertise/curation",
    );
  });

  it("highlights only Curation (not Expertise) on the curation route", () => {
    vi.mocked(useManagementAccess).mockReturnValueOnce({
      ...noAccess,
      curation: true,
      health: true,
      any: true,
    });
    renderShell("/communities/backend-guild/expertise/curation");
    const rail = within(screen.getByTestId("community-rail"));
    expect(rail.getByRole("link", { name: "Curation" })).toHaveAttribute(
      "aria-current",
      "page",
    );
    expect(rail.getByRole("link", { name: "Expertise" })).not.toHaveAttribute(
      "aria-current",
    );
  });

  it("groups the desktop sections under category labels", () => {
    renderShell();
    const rail = within(screen.getByTestId("community-rail"));
    expect(rail.getByText("Engage")).toBeInTheDocument();
    expect(rail.getByText("Knowledge")).toBeInTheDocument();
    expect(rail.getByRole("link", { name: "Expertise" })).toHaveAttribute(
      "href",
      "/communities/backend-guild/expertise",
    );
  });
});
