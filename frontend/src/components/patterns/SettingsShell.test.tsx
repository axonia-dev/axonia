import { render, renderHook, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import type { ReactNode } from "react";
import { MemoryRouter, Route, Routes } from "react-router-dom";
import { beforeEach, describe, expect, it, vi } from "vitest";

import {
  organizationSettingsGroups,
  visibleGroups,
} from "@/components/patterns/settings-nav";
import {
  forgetAppLocation,
  useRememberAppLocation,
} from "@/components/patterns/settings-return";
import { SettingsShell } from "@/components/patterns/SettingsShell";
import { SidebarProvider } from "@/components/patterns/SidebarProvider";
import { useCurrentOrganization } from "@/features/admin/api/use-current-organization";
import { ThemeProvider } from "@/theme/ThemeProvider";

vi.mock("@/auth/session", () => ({
  useAuth: () => ({
    user: { profile: { preferred_username: "alice", sub: "sub-1" } },
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
vi.mock("@/features/admin/api/use-current-organization", () => ({
  useCurrentOrganization: vi.fn(() => ({ data: { kind: "ENTERPRISE" } })),
}));
// AccountMenu (in the mobile top bar) needs this to decide whether to offer Organization settings; not what these tests are about.
vi.mock("@/features/admin/api/use-permissions", () => ({
  usePermissions: () => ({ can: () => true, isLoading: false }),
}));
// The notification bell in both top bars; no QueryClientProvider here, so its query is stubbed.
vi.mock("@/features/communication/api/use-unread-count", () => ({
  useUnreadCount: () => ({ data: 4 }),
}));
vi.mock("@/features/onboarding/api/use-onboarding", () => ({
  useOnboarding: () => ({ data: undefined }),
  useRecordVisit: () => ({ mutate: vi.fn() }),
  useCloseOnboarding: () => ({ mutate: vi.fn() }),
}));
// The rail's tenant identity chip: OrganizationSwitcher reads the membership list directly, not `useCurrentOrganization`, so it needs its own real stub.
vi.mock("@/features/admin/api/use-my-organizations", () => ({
  useMyOrganizations: () => ({
    data: [{ id: "default", name: "Acme Engineering", badgeUrl: null }],
  }),
}));
// The HUB branch of the same chip (CommunityIdentityChip, fed by the caller's own one-community list) - a stub community, only exercised by the HUB-kind tests below.
const acmeHub = {
  id: 1,
  slug: "acme-hub",
  name: "Acme Hub",
  kindId: 1,
  memberCount: 42,
};
vi.mock("@/features/community/api/use-communities", () => ({
  useCommunities: () => ({ data: { content: [acmeHub] } }),
}));

const mockUseCurrentOrganization = vi.mocked(useCurrentOrganization);
function renderShell(
  options: Readonly<{
    base?: string;
    backTo?: string;
    backTabLabel?: string;
    initialPath?: string;
  }> = {},
) {
  const {
    base = "/settings/organization",
    backTo = "/communities",
    backTabLabel = "Communities",
    initialPath = "/settings/organization/users",
  } = options;
  const groups = visibleGroups(organizationSettingsGroups("ENTERPRISE", []), {
    can: () => true,
    delegated: false,
  });
  return render(
    <ThemeProvider>
      <SidebarProvider>
        <MemoryRouter initialEntries={[initialPath]}>
          <Routes>
            <Route
              element={
                <SettingsShell
                  base={base}
                  title="Organization settings"
                  backTo={backTo}
                  backTabLabel={backTabLabel}
                  groups={groups}
                />
              }
            >
              <Route path={`${base}/users`} element={<p>Users page</p>} />
            </Route>
          </Routes>
        </MemoryRouter>
      </SidebarProvider>
    </ThemeProvider>,
  );
}

/** Both bars render at once in jsdom (mobile is hidden by CSS alone), so every heading assertion scopes to the bar it means. */
const desktopBar = () => within(screen.getByTestId("settings-desktop-topbar"));
const mobileBar = () => within(screen.getByTestId("settings-topbar"));

/** Puts the member somewhere in the app, as having been there before opening settings. */
function rememberVisit(path: string) {
  renderHook(() => useRememberAppLocation(), {
    wrapper: ({ children }: Readonly<{ children: ReactNode }>) => (
      <MemoryRouter initialEntries={[path]}>{children}</MemoryRouter>
    ),
  }).unmount();
}

describe("SettingsShell", () => {
  beforeEach(() => {
    localStorage.clear();
    forgetAppLocation();
    mockUseCurrentOrganization.mockReturnValue({
      data: { kind: "ENTERPRISE" },
    } as never);
  });

  it("replaces the app rail with the settings sections", () => {
    renderShell();
    const rail = within(screen.getByTestId("settings-rail"));
    // The full ordered link set, not just an absent "Communities" tautology: this fails the
    // moment anything other than the catalogue's own sections leaks in (a stray primary destination, or the way out returning as a card).
    const railLinkNames = rail
      .getAllByRole("link")
      .map((link) => link.textContent);
    expect(railLinkNames).toEqual([
      "General",
      "Vocabulary",
      "Notifications",
      "Users",
      "Roles",
      "Group access",
      "Directory sync",
      "Data export",
    ]);
    expect(rail.getByRole("link", { name: "Users" })).toHaveAttribute(
      "href",
      "/settings/organization/users",
    );
    expect(
      rail.queryByText("Organization", { selector: "p" }),
    ).not.toBeInTheDocument();
    // No header text is drawn (chrome: "none"), but the landmark still carries the group's
    // name so it stays distinguishable from the other <nav> regions in the rail.
    expect(
      rail.getByRole("navigation", { name: "Organization" }),
    ).toBeInTheDocument();
    expect(
      rail.getByText("People & access", { selector: "p" }),
    ).toBeInTheDocument();
  });

  it("leaves the mode the same way at every width, and never from the rail", () => {
    renderShell();
    for (const bar of [desktopBar(), mobileBar()]) {
      expect(
        bar.getByRole("link", { name: "Back to the app" }),
      ).toHaveAttribute("href", "/communities");
      expect(bar.getByText("Organization settings")).toBeInTheDocument();
    }
    const rail = within(screen.getByTestId("settings-rail"));
    expect(rail.queryByRole("link", { name: /^Back to/ })).toBeNull();
  });

  it("goes back to what the member was doing, not to a fixed list", () => {
    rememberVisit("/communities/acme/events?tab=past");
    renderShell();
    expect(
      desktopBar().getByRole("link", { name: "Back to the app" }),
    ).toHaveAttribute("href", "/communities/acme/events?tab=past");
    // The tab bar keeps the fixed destination: a tab names a place, the arrow means back.
    const tabs = within(screen.getByTestId("settings-tabs"));
    expect(tabs.getByRole("link", { name: "Communities" })).toHaveAttribute(
      "href",
      "/communities",
    );
  });

  it("names the surface, and nothing the rail already says", () => {
    // Deliberately not a breadcrumb: settings is reached from the account menu, not by
    // drilling down, so a trail would invent a parent - and the lit rail item plus the page's own heading already name the section twice over.
    renderShell();
    const bar = desktopBar();
    expect(bar.getByText("Organization settings")).toBeInTheDocument();
    expect(bar.queryByRole("navigation", { name: "Breadcrumb" })).toBeNull();
    expect(bar.queryByText("Users")).toBeNull();
  });

  it("falls back to the surface's own destination when there is no history", () => {
    // A hub's settings sit under its own community, so a cold open - a bookmark, a reload, a legacy /admin/* path - lands there rather than at the top-level list.
    renderShell({
      base: "/communities/acme-hub/settings/organization",
      backTo: "/communities/acme-hub",
      backTabLabel: "Community",
      initialPath: "/communities/acme-hub/settings/organization/users",
    });
    expect(
      desktopBar().getByRole("link", { name: "Back to the app" }),
    ).toHaveAttribute("href", "/communities/acme-hub");
  });

  it("carries the account menu in the desktop top bar too", async () => {
    const user = userEvent.setup({ delay: null });
    renderShell();
    const topbar = within(screen.getByTestId("settings-desktop-topbar"));
    await user.click(topbar.getByRole("button", { name: "Account" }));
    expect(screen.getByText("Alice Anderson")).toBeInTheDocument();
  });

  it("shows the tenant identity chip above the sections", () => {
    renderShell();
    const rail = within(screen.getByTestId("settings-rail"));
    const identity = rail.getByLabelText("Organization: Acme Engineering");
    const first = rail.getByRole("link", { name: "General" });
    // DOCUMENT_POSITION_FOLLOWING: `first` comes after `identity` in the DOM.
    expect(
      identity.compareDocumentPosition(first) &
        Node.DOCUMENT_POSITION_FOLLOWING,
    ).toBeTruthy();
  });

  it("shows a hub's community identity instead of the organization switcher", () => {
    mockUseCurrentOrganization.mockReturnValue({
      data: { kind: "HUB" },
    } as never);
    renderShell();
    const rail = within(screen.getByTestId("settings-rail"));
    expect(rail.getByLabelText("Community: Acme Hub")).toBeInTheDocument();
    expect(rail.queryByLabelText(/^Organization:/)).not.toBeInTheDocument();
  });

  it("keeps the identity chip when the rail collapses", async () => {
    const user = userEvent.setup({ delay: null });
    renderShell();
    await user.click(screen.getByRole("button", { name: "Collapse sidebar" }));
    const rail = within(screen.getByTestId("settings-rail"));
    expect(
      rail.getByLabelText("Organization: Acme Engineering"),
    ).toBeInTheDocument();
    // The way out survives the collapse because it never lived in the rail: it sits in
    // the top bar, which the collapse does not touch.
    expect(
      desktopBar().getByRole("link", { name: "Back to the app" }),
    ).toBeInTheDocument();
  });

  it("renders the routed section", () => {
    renderShell();
    expect(screen.getByText("Users page")).toBeInTheDocument();
  });

  it("marks the mobile Settings tab active on a section page, not the way out", () => {
    renderShell();
    const tabs = within(screen.getByTestId("settings-tabs"));
    expect(tabs.getByRole("link", { name: "Communities" })).not.toHaveAttribute(
      "aria-current",
    );
    expect(tabs.getByRole("link", { name: "Settings" })).toHaveAttribute(
      "aria-current",
      "page",
    );
  });

  it("does not light the back tab for every page under a hub's own community, an ancestor of the whole settings surface", () => {
    renderShell({
      base: "/communities/acme-hub/settings/organization",
      backTo: "/communities/acme-hub",
      initialPath: "/communities/acme-hub/settings/organization/users",
    });
    const tabs = within(screen.getByTestId("settings-tabs"));
    expect(tabs.getByRole("link", { name: "Communities" })).not.toHaveAttribute(
      "aria-current",
    );
    expect(tabs.getByRole("link", { name: "Settings" })).toHaveAttribute(
      "aria-current",
      "page",
    );
  });
});
