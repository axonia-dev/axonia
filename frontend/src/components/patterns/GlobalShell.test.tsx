import { render, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter, Route, Routes } from "react-router-dom";
import { describe, expect, it, vi } from "vitest";

import { GlobalShell } from "@/components/patterns/GlobalShell";
import { SidebarProvider } from "@/components/patterns/SidebarProvider";
import { usePermissions } from "@/features/admin/api/use-permissions";
import { permissionState } from "@/test/permissions";
import { ThemeProvider } from "@/theme/ThemeProvider";

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
vi.mock("@/features/admin/api/use-permissions", () => ({
  usePermissions: vi.fn(),
}));

// A write hook with a query client behind it, mounted here for the reason the visit recorder is:
// the header slots below render twice, so anything that writes has to be mounted once, above them.
vi.mock("@/features/identity/api/use-time-zone", () => ({
  useReportObservedZone: () => undefined,
}));

// No QueryClientProvider here, so both OrganizationSwitcher's and AccountMenu's queries are stubbed.
vi.mock("@/features/admin/api/use-current-organization", () => ({
  useCurrentOrganization: () => ({ data: undefined }),
}));
vi.mock("@/features/admin/ui/OrganizationSwitcher", () => ({
  OrganizationSwitcher: () => null,
}));
vi.mock("@/features/communication/api/use-unread-count", () => ({
  useUnreadCount: () => ({ data: 2 }),
}));
vi.mock("@/features/onboarding/api/use-onboarding", () => ({
  useOnboarding: () => ({ data: undefined }),
  useRecordVisit: () => ({ mutate: vi.fn() }),
  useCloseOnboarding: () => ({ mutate: vi.fn() }),
}));

const mockUsePermissions = vi.mocked(usePermissions);

function renderShell() {
  return render(
    <ThemeProvider>
      <SidebarProvider>
        <MemoryRouter initialEntries={["/communities"]}>
          <Routes>
            <Route element={<GlobalShell />}>
              <Route path="/communities" element={<p>Page body</p>} />
            </Route>
          </Routes>
        </MemoryRouter>
      </SidebarProvider>
    </ThemeProvider>,
  );
}

describe("GlobalShell", () => {
  it("keeps only destinations in the rail", () => {
    mockUsePermissions.mockReturnValue(permissionState(() => true));
    renderShell();
    const rail = within(screen.getByTestId("global-rail"));
    expect(rail.getByRole("link", { name: "Communities" })).toBeInTheDocument();
    expect(rail.getByRole("link", { name: "My impact" })).toBeInTheDocument();
    expect(rail.queryByRole("link", { name: "Users" })).toBeNull();
    expect(rail.queryByRole("link", { name: "API keys" })).toBeNull();
    expect(rail.queryByRole("link", { name: "Docs" })).toBeNull();
  });

  it("renders a desktop top bar carrying the bell and the account menu, no left content", () => {
    mockUsePermissions.mockReturnValue(permissionState(() => true));
    renderShell();
    // GlobalShell passes no `desktopHeader` content, so AppShell's own bar - a
    // sibling <header> of the mobile top bar - carries nothing on its left: only
    // the two right-hand controls, the notification bell and the account menu.
    const topbar = within(screen.getByTestId("global-desktop-topbar"));
    expect(
      topbar.getByRole("button", { name: "Notifications, 2 unread" }),
    ).toBeInTheDocument();
    expect(topbar.getByRole("button", { name: "Account" })).toBeInTheDocument();
    expect(topbar.queryByRole("link")).toBeNull();
  });

  it("puts the bell in the mobile top bar too", () => {
    mockUsePermissions.mockReturnValue(permissionState(() => true));
    renderShell();
    const topbar = within(screen.getByTestId("global-topbar"));
    expect(
      topbar.getByRole("button", { name: "Notifications, 2 unread" }),
    ).toBeInTheDocument();
  });

  // A literal `getAllByRole("link")` assertion can never include
  // "Me": it is a `kind: "menu"` tab, rendered as a <button> that opens a
  // sheet, not a NavLink - the same distinction the mobile bar has always
  // drawn between a destination and a collapsed group. Querying both roles
  // in DOM order keeps the same intent (the three-tab bar, in this order)
  // without asserting something ShellTabBar cannot produce.
  it("offers Communities, Me and Settings on the mobile bar", () => {
    mockUsePermissions.mockReturnValue(permissionState(() => true));
    renderShell();
    const tabs = screen.getByTestId("global-tabs");
    const labels = [...tabs.querySelectorAll("a, button")].map(
      (el) => el.textContent,
    );
    expect(labels).toEqual(["Communities", "Me", "Settings"]);
  });

  it("does not show a Hubs nav group in the desktop rail", () => {
    mockUsePermissions.mockReturnValue(permissionState(() => true));
    renderShell();
    const rail = within(screen.getByTestId("global-rail"));
    expect(rail.queryByText("Hubs")).not.toBeInTheDocument();
    expect(rail.queryByRole("link", { name: "Home" })).not.toBeInTheDocument();
    expect(
      rail.queryByRole("link", { name: "Discover" }),
    ).not.toBeInTheDocument();
  });

  it("moves the account menu out of the rail and into the desktop top bar", async () => {
    const user = userEvent.setup({ delay: null });
    mockUsePermissions.mockReturnValue(permissionState(() => true));
    renderShell();
    const rail = within(screen.getByTestId("global-rail"));
    expect(
      rail.queryByRole("button", { name: "Account" }),
    ).not.toBeInTheDocument();
    const topbar = within(screen.getByTestId("global-desktop-topbar"));
    const trigger = topbar.getByRole("button", { name: "Account" });
    await user.click(trigger);
    expect(screen.getByText("Alice Anderson")).toBeInTheDocument();
  });

  it("shows Communities as a link and Me as a sheet-opening button on the mobile bar", () => {
    mockUsePermissions.mockReturnValue(permissionState(() => true));
    renderShell();
    const tabs = within(screen.getByTestId("global-tabs"));
    expect(tabs.getByRole("link", { name: "Communities" })).toHaveAttribute(
      "href",
      "/communities",
    );
    expect(tabs.getByRole("button", { name: "Me" })).toBeInTheDocument();
  });

  it("opens the Me sheet with the Activity destinations, ungated", async () => {
    const user = userEvent.setup({ delay: null });
    mockUsePermissions.mockReturnValue(permissionState(() => false));
    renderShell();
    const tabs = within(screen.getByTestId("global-tabs"));
    await user.click(tabs.getByRole("button", { name: "Me" }));

    const sheet = within(screen.getByRole("dialog"));
    expect(sheet.getByRole("link", { name: /my impact/i })).toHaveAttribute(
      "href",
      "/me/impact",
    );
    expect(sheet.getByRole("link", { name: /my expertise/i })).toHaveAttribute(
      "href",
      "/me/expertise",
    );
    expect(sheet.getByRole("link", { name: /saved/i })).toHaveAttribute(
      "href",
      "/me/saved",
    );
  });

  it("points the Settings tab at account settings", () => {
    mockUsePermissions.mockReturnValue(permissionState(() => true));
    renderShell();
    const tabs = within(screen.getByTestId("global-tabs"));
    expect(tabs.getByRole("link", { name: "Settings" })).toHaveAttribute(
      "href",
      "/settings/account",
    );
  });
});
