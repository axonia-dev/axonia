import { render, screen, within } from "@testing-library/react";
import { MemoryRouter, Routes } from "react-router-dom";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

import { SidebarProvider } from "@/components/patterns/SidebarProvider";
import { useCurrentOrganization } from "@/features/admin/api/use-current-organization";
import { usePermissions } from "@/features/admin/api/use-permissions";
import { useAgentAccess } from "@/features/agent/api/use-personal-access-tokens";
import { openSlots } from "@/open-slots";
import { SettingsRoutes } from "@/routes/settings-routes";
import { signInScopedToForEachTest } from "@/test/access-token";
import { AGENT_ACCESS } from "@/test/agent-access";
import { permissionState } from "@/test/permissions";
import { ThemeProvider } from "@/theme/ThemeProvider";

// The settings landing (the base `/settings/account` and `/settings/organization`
// paths) skips the overview list on desktop and opens the first real section directly;
// below `md` the overview stays. SettingsLanding.test.tsx covers the component in
// isolation; this file proves the real catalogue + real route registration wire up the
// same way (in particular, that `general` really is the organization surface's first
// section, matching settings-nav's own ordering).

vi.mock("@/features/identity/ui/AccountProfilePage", () => ({
  AccountProfilePage: () => <p>Profile page</p>,
}));
vi.mock("@/features/admin/ui/TenantSettingsPage", () => ({
  TenantSettingsPage: () => <p>Tenant settings page</p>,
}));
vi.mock("@/auth/session", () => ({
  useAuth: () => ({
    user: { profile: { preferred_username: "alice", sub: "sub-1" } },
    signoutRedirect: vi.fn(),
  }),
}));
vi.mock("@/features/identity/api/use-me", () => ({
  useMe: () => ({
    data: { id: "sub-1", displayName: "Alice Anderson", email: null },
  }),
}));
vi.mock("@/features/admin/api/use-current-organization", () => ({
  useCurrentOrganization: vi.fn(),
}));
vi.mock("@/features/admin/api/use-permissions", () => ({
  usePermissions: vi.fn(),
}));
vi.mock("@/features/agent/api/use-personal-access-tokens", () => ({
  useAgentAccess: vi.fn(),
}));
// The notification bell in the shell top bars; no QueryClientProvider here, so its query is stubbed.
vi.mock("@/features/communication/api/use-unread-count", () => ({
  useUnreadCount: () => ({ data: 0 }),
}));
vi.mock("@/features/onboarding/api/use-onboarding", () => ({
  useOnboarding: () => ({ data: undefined }),
  useRecordVisit: () => ({ mutate: vi.fn() }),
  useCloseOnboarding: () => ({ mutate: vi.fn() }),
}));
// SettingsShell now shows a tenant identity chip in the rail; this file is about the
// landing redirect, not the chip's own rendering (SettingsShell.test.tsx covers that).
vi.mock("@/features/admin/ui/OrganizationSwitcher", () => ({
  OrganizationSwitcher: () => null,
}));
vi.mock("@/features/community/api/use-communities", () => ({
  useCommunities: () => ({ data: undefined }),
}));
vi.mock("@/auth/delegated-session", () => ({
  useIsDelegated: () => false,
}));

const mockUseCurrentOrganization = vi.mocked(useCurrentOrganization);
const mockUsePermissions = vi.mocked(usePermissions);
const mockUseAgentAccess = vi.mocked(useAgentAccess);

const originalMatchMedia = window.matchMedia;

function stubMatchMedia(matches: boolean) {
  window.matchMedia = vi.fn().mockReturnValue({
    matches,
    media: "",
    onchange: null,
    addEventListener: vi.fn(),
    removeEventListener: vi.fn(),
    addListener: vi.fn(),
    removeListener: vi.fn(),
    dispatchEvent: vi.fn(),
  });
}

signInScopedToForEachTest("org-1");

beforeEach(() => {
  mockUseCurrentOrganization.mockReturnValue({
    data: { id: "org-1", name: "Default Org", kind: "ENTERPRISE" },
  } as never);
  mockUsePermissions.mockReturnValue(permissionState(() => true));
  mockUseAgentAccess.mockReturnValue(AGENT_ACCESS);
});

afterEach(() => {
  window.matchMedia = originalMatchMedia;
});

function renderAt(path: string) {
  return render(
    <ThemeProvider>
      <SidebarProvider>
        <MemoryRouter initialEntries={[path]}>
          <Routes>{SettingsRoutes({ base: "", edition: openSlots })}</Routes>
        </MemoryRouter>
      </SidebarProvider>
    </ThemeProvider>,
  );
}

describe("the settings landing on desktop", () => {
  it("opens the tenant section directly instead of the organization overview list", () => {
    stubMatchMedia(true);
    renderAt("/settings/organization");
    expect(screen.getByText("Tenant settings page")).toBeInTheDocument();
    expect(
      screen.queryByRole("heading", { name: "Organization" }),
    ).not.toBeInTheDocument();
  });

  it("opens the account profile directly instead of the account overview list", () => {
    stubMatchMedia(true);
    renderAt("/settings/account");
    expect(screen.getByText("Profile page")).toBeInTheDocument();
    expect(
      screen.queryByRole("heading", { name: "Account" }),
    ).not.toBeInTheDocument();
  });
});

describe("the settings landing below md", () => {
  it("keeps showing the organization overview list, with a link into the tenant section", () => {
    stubMatchMedia(false);
    renderAt("/settings/organization");
    expect(
      screen.getByRole("heading", { name: "Organization" }),
    ).toBeInTheDocument();
    expect(screen.queryByText("Tenant settings page")).not.toBeInTheDocument();
    const main = within(screen.getByRole("main"));
    expect(main.getByRole("link", { name: "General" })).toHaveAttribute(
      "href",
      "/settings/organization/general",
    );
  });

  it("keeps showing the account overview list", () => {
    stubMatchMedia(false);
    renderAt("/settings/account");
    expect(
      screen.getByRole("heading", { name: "Account" }),
    ).toBeInTheDocument();
    expect(screen.queryByText("Profile page")).not.toBeInTheDocument();
  });
});
