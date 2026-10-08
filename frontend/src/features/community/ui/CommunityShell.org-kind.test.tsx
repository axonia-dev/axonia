import { render, screen, within } from "@testing-library/react";
import { MemoryRouter, Route, Routes } from "react-router-dom";
import { afterEach, describe, expect, it, vi } from "vitest";

import { SidebarProvider } from "@/components/patterns/SidebarProvider";
import { useCurrentOrganization } from "@/features/admin/api/use-current-organization";
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

// Defaults to a pending/enterprise-like org so any test that forgets to opt in
// still exercises the plain (non-hub) shell rather than erroring on missing data.
vi.mock("@/features/admin/api/use-current-organization", () => ({
  useCurrentOrganization: vi.fn(() => ({ data: undefined })),
}));

vi.mock("@/features/admin/api/use-permissions", () => ({
  usePermissions: vi.fn(() => ({ can: () => false, isLoading: false })),
}));
// The notification bell in the shell top bars; no QueryClientProvider here, so its query is stubbed.
vi.mock("@/features/communication/api/use-unread-count", () => ({
  useUnreadCount: () => ({ data: 0 }),
}));

// The onboarding affordance in both top bars; no QueryClientProvider here, so its query is stubbed.
vi.mock("@/features/onboarding/api/use-onboarding", () => ({
  useOnboarding: () => ({ data: undefined }),
  useRecordVisit: () => ({ mutate: vi.fn() }),
  useAssignOnboarding: () => ({ mutate: vi.fn() }),
  useCloseOnboarding: () => ({ mutate: vi.fn() }),
}));

// Both chips share the "Switch community" accessible label (see
// CommunitySwitcher.tsx), so the hub's identity chip is mocked to a
// distinguishable stub rather than asserted on by label.
vi.mock("@/features/community/ui/CommunityIdentityChip", () => ({
  CommunityIdentityChip: ({ current }: { current: { name: string } }) => (
    <div data-testid="hub-switcher">{current.name}</div>
  ),
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

function renderShell(location = "/communities/backend-guild") {
  render(
    <ThemeProvider>
      <SidebarProvider>
        <MemoryRouter initialEntries={[location]}>
          <Routes>
            <Route path="/communities/:slug" element={<CommunityShell />}>
              <Route index element={<p>overview-panel</p>} />
              <Route path="me/expertise" element={<p>my-expertise-panel</p>} />
            </Route>
          </Routes>
        </MemoryRouter>
      </SidebarProvider>
    </ThemeProvider>,
  );
}

describe("CommunityShell by organization kind", () => {
  function mockHub() {
    vi.mocked(useCurrentOrganization).mockReturnValue({
      data: { id: "org-1", name: "Backend Guild", kind: "HUB" },
    } as never);
  }

  // The ordering test grants management access; restore the no-access default so
  // it does not leak a "Manage" group into the other cases (no auto mock reset).
  afterEach(() => {
    vi.mocked(useManagementAccess).mockReturnValue(noAccess);
  });

  // Admin is gone from this rail entirely (organization administration now lives
  // behind the account menu's Organization settings - CommunityShell no longer
  // calls usePermissions at all), so there is no admin destination left anywhere
  // to gate. "Me" renders for every organization kind.
  it("shows the Me group for a HUB org, with no Admin group at all", () => {
    mockHub();
    renderShell();
    const rail = within(screen.getByTestId("community-rail"));
    expect(rail.getByText("Me")).toBeInTheDocument();
    expect(rail.getByRole("link", { name: "My impact" })).toHaveAttribute(
      "href",
      "/communities/backend-guild/me/impact",
    );
    expect(rail.getByRole("link", { name: "My expertise" })).toHaveAttribute(
      "href",
      "/communities/backend-guild/me/expertise",
    );
    expect(rail.getByRole("link", { name: "Saved" })).toHaveAttribute(
      "href",
      "/communities/backend-guild/me/saved",
    );
    expect(rail.queryByText("Admin")).not.toBeInTheDocument();
    expect(rail.queryByRole("link", { name: "Users" })).not.toBeInTheDocument();
    expect(rail.queryByRole("link", { name: "Roles" })).not.toBeInTheDocument();
    expect(
      rail.queryByRole("link", { name: "Vocabulary" }),
    ).not.toBeInTheDocument();
  });

  it("orders the rail groups Engage → Knowledge → Me → Manage", () => {
    mockHub();
    // Grant management access so the "Manage" group renders after Me.
    vi.mocked(useManagementAccess).mockReturnValue({
      ...noAccess,
      curation: true,
      health: true,
      any: true,
    });
    renderShell();
    const rail = within(screen.getByTestId("community-rail"));
    const order = ["Engage", "Knowledge", "Me", "Manage"];
    const rendered = rail
      .getAllByRole("navigation")
      .map((nav) => nav.getAttribute("aria-label"))
      .filter((label): label is string => order.includes(label ?? ""));
    expect(rendered).toEqual(order);
  });

  it("drops the Communities breadcrumb root and swaps in the community identity chip for a HUB org", () => {
    mockHub();
    renderShell();
    const breadcrumb = within(
      screen.getByRole("navigation", { name: "Breadcrumb" }),
    );
    expect(
      breadcrumb.queryByRole("link", { name: "Communities" }),
    ).not.toBeInTheDocument();
    // With no "Communities" root, the community name is the lone (and thus
    // current-page) crumb, so Breadcrumb renders it as text, not a link.
    expect(breadcrumb.getByText("Backend Guild")).toHaveAttribute(
      "aria-current",
      "page",
    );
    // Rendered once in the desktop rail and once in the mobile topbar.
    expect(screen.getAllByTestId("hub-switcher")).toHaveLength(2);
  });

  it("shows the Me group for an ENTERPRISE org too, with no Admin group", () => {
    vi.mocked(useCurrentOrganization).mockReturnValue({
      data: { id: "org-1", name: "Backend Guild", kind: "ENTERPRISE" },
    } as never);
    renderShell();
    const rail = within(screen.getByTestId("community-rail"));
    expect(rail.getByText("Me")).toBeInTheDocument();
    expect(rail.getByRole("link", { name: "My impact" })).toHaveAttribute(
      "href",
      "/communities/backend-guild/me/impact",
    );
    expect(rail.getByRole("link", { name: "My expertise" })).toHaveAttribute(
      "href",
      "/communities/backend-guild/me/expertise",
    );
    expect(rail.getByRole("link", { name: "Saved" })).toHaveAttribute(
      "href",
      "/communities/backend-guild/me/saved",
    );
    expect(rail.queryByText("Admin")).not.toBeInTheDocument();
    expect(screen.getByRole("link", { name: "Communities" })).toHaveAttribute(
      "href",
      "/communities",
    );
  });

  it("lights My expertise, not the knowledge Expertise item, on the member's own expertise page", () => {
    vi.mocked(useCurrentOrganization).mockReturnValue({
      data: { id: "org-1", name: "Backend Guild", kind: "ENTERPRISE" },
    } as never);
    renderShell("/communities/backend-guild/me/expertise");
    const rail = within(screen.getByTestId("community-rail"));
    expect(rail.getByRole("link", { name: "My expertise" })).toHaveAttribute(
      "aria-current",
      "page",
    );
    expect(rail.getByRole("link", { name: "Expertise" })).not.toHaveAttribute(
      "aria-current",
    );
  });
});
