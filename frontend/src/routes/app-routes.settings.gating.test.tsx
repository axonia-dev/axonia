import { render, screen, within } from "@testing-library/react";
import { MemoryRouter, Route, Routes } from "react-router-dom";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

import { useIsDelegated } from "@/auth/delegated-session";
import { SidebarProvider } from "@/components/patterns/SidebarProvider";
import { useCurrentOrganization } from "@/features/admin/api/use-current-organization";
import { usePermissions } from "@/features/admin/api/use-permissions";
import { useAgentAccess } from "@/features/agent/api/use-personal-access-tokens";
import { openSlots } from "@/open-slots";
import { SettingsRoutes } from "@/routes/settings-routes";
import { signInScopedToForEachTest } from "@/test/access-token";
import {
  AGENT_ACCESS,
  AGENT_ACCESS_LOADING,
  NO_AGENT_ACCESS,
} from "@/test/agent-access";
import { permissionState } from "@/test/permissions";
import { forceMobileViewport, restoreViewport } from "@/test/viewport";
import { ThemeProvider } from "@/theme/ThemeProvider";

// Split out of app-routes.settings.test.tsx (300-line ceiling): who gets redirected
// away from what (privacy's own data-rights gate, and the organization surface's
// permission gate) is a distinct concern from that file's routing-table coverage
// (which page a path resolves to).

vi.mock("@/features/privacy/ui/PrivacySettingsPage", () => ({
  PrivacySettingsPage: () => <p>Privacy page</p>,
}));
vi.mock("@/features/admin/ui/TenantSettingsPage", () => ({
  TenantSettingsPage: () => <p>Tenant settings page</p>,
}));
vi.mock("@/features/agent/ui/ApiKeysView", () => ({
  ApiKeysView: () => <p>API keys page</p>,
}));
vi.mock("@/features/agent/api/use-personal-access-tokens", () => ({
  useAgentAccess: vi.fn(),
}));

// SettingsShell always renders AccountMenu (mobile top bar), which needs auth + /me.
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
// The notification bell in the shell top bars; no QueryClientProvider here, so its query is stubbed.
vi.mock("@/features/communication/api/use-unread-count", () => ({
  useUnreadCount: () => ({ data: 0 }),
}));
vi.mock("@/features/onboarding/api/use-onboarding", () => ({
  useOnboarding: () => ({ data: undefined }),
  useRecordVisit: () => ({ mutate: vi.fn() }),
  useCloseOnboarding: () => ({ mutate: vi.fn() }),
}));
// SettingsShell now shows a tenant identity chip in the rail; these gating tests are
// about redirect targets, not the chip's own rendering (SettingsShell.test.tsx covers that).
vi.mock("@/features/admin/ui/OrganizationSwitcher", () => ({
  OrganizationSwitcher: () => null,
}));
vi.mock("@/features/community/api/use-communities", () => ({
  useCommunities: () => ({ data: undefined }),
}));
vi.mock("@/auth/delegated-session", () => ({
  useIsDelegated: vi.fn(),
}));

const mockUseCurrentOrganization = vi.mocked(useCurrentOrganization);
const mockUsePermissions = vi.mocked(usePermissions);
const mockUseIsDelegated = vi.mocked(useIsDelegated);
const mockUseAgentAccess = vi.mocked(useAgentAccess);

signInScopedToForEachTest("org-1");

beforeEach(() => {
  forceMobileViewport();
  mockUseCurrentOrganization.mockReturnValue({
    data: { id: "org-1", name: "Default Org", kind: "ENTERPRISE" },
  } as never);
  mockUsePermissions.mockReturnValue(permissionState(() => true));
  mockUseIsDelegated.mockReturnValue(false);
  mockUseAgentAccess.mockReturnValue(AGENT_ACCESS);
});

afterEach(restoreViewport);

function renderRoutes(base: string, path: string) {
  return render(
    <ThemeProvider>
      <SidebarProvider>
        <MemoryRouter initialEntries={[path]}>
          <Routes>{SettingsRoutes({ base, edition: openSlots })}</Routes>
        </MemoryRouter>
      </SidebarProvider>
    </ThemeProvider>,
  );
}

describe("the account privacy destination", () => {
  it("routes to it for a hub session with its own data rights", () => {
    mockUseCurrentOrganization.mockReturnValue({
      data: { id: "org-1", name: "Acme Hub", kind: "HUB" },
    } as never);
    renderRoutes("", "/settings/account/privacy");
    expect(screen.getByText("Privacy page")).toBeInTheDocument();
  });

  it("is not reachable for an enterprise session", () => {
    renderRoutes("", "/settings/account/privacy");
    expect(screen.queryByText("Privacy page")).not.toBeInTheDocument();
    expect(
      screen.getByRole("heading", { name: "Account" }),
    ).toBeInTheDocument();
  });

  it("is not reachable for a delegated tab standing in a hub member's session", () => {
    mockUseCurrentOrganization.mockReturnValue({
      data: { id: "org-1", name: "Acme Hub", kind: "HUB" },
    } as never);
    mockUseIsDelegated.mockReturnValue(true);
    renderRoutes("", "/settings/account/privacy");
    expect(screen.queryByText("Privacy page")).not.toBeInTheDocument();
    expect(
      screen.getByRole("heading", { name: "Account" }),
    ).toBeInTheDocument();
  });

  it("routes to it for a hub member nested under their community", () => {
    mockUseCurrentOrganization.mockReturnValue({
      data: { id: "org-1", name: "Acme Hub", kind: "HUB" },
    } as never);
    renderRoutes(
      "/communities/:slug",
      "/communities/acme-hub/settings/account/privacy",
    );
    expect(screen.getByText("Privacy page")).toBeInTheDocument();
  });

  it("is not reachable for an enterprise member nested under their community", () => {
    renderRoutes(
      "/communities/:slug",
      "/communities/acme-hub/settings/account/privacy",
    );
    expect(screen.queryByText("Privacy page")).not.toBeInTheDocument();
    expect(
      screen.getByRole("heading", { name: "Account" }),
    ).toBeInTheDocument();
  });

  it("does not eject a hub member from privacy while the organization is still loading", () => {
    mockUseCurrentOrganization.mockReturnValue({
      data: undefined,
      isError: false,
      errorUpdateCount: 0,
    } as never);
    renderRoutes("", "/settings/account/privacy");
    // Neither the page itself, the redirect's landing spot (the overview) nor a failure has
    // rendered: a cold mount waits rather than reading "not loaded yet" as "not entitled" or
    // as "could not load".
    expect(screen.queryByText("Privacy page")).not.toBeInTheDocument();
    expect(
      screen.queryByRole("heading", { name: "Account" }),
    ).not.toBeInTheDocument();
    expect(screen.queryByRole("alert")).not.toBeInTheDocument();
    expect(
      screen.queryByRole("heading", { name: "Privacy" }),
    ).not.toBeInTheDocument();
  });
});

describe("organization settings with no visible sections", () => {
  it("reaches the surface and sees only the tenant section, for a member with no organization permission", () => {
    // The tenant section is ungated by design: switching tenants moved here off
    // the rail chip, so a plain member without any organization permission must still
    // reach it - this reverses the old rule (redirected away entirely) on purpose.
    mockUsePermissions.mockReturnValue(permissionState(() => false));
    render(
      <ThemeProvider>
        <SidebarProvider>
          <MemoryRouter initialEntries={["/settings/organization"]}>
            <Routes>
              {SettingsRoutes({ base: "", edition: openSlots })}
              <Route path="/communities" element={<p>Communities landing</p>} />
            </Routes>
          </MemoryRouter>
        </SidebarProvider>
      </ThemeProvider>,
    );
    expect(screen.queryByText("Communities landing")).not.toBeInTheDocument();
    expect(
      screen.getByRole("heading", { name: "Organization" }),
    ).toBeInTheDocument();
    // Exactly the ungated tenant section - every permissioned section is gone. The lead
    // group's own header (which would just restate "Organization" again) does not render
    // because organizationSettingsGroups gives it an explicit chrome: "none", not because
    // it happens to be the only group left.
    const main = within(screen.getByRole("main"));
    expect(main.getByRole("link", { name: "General" })).toHaveAttribute(
      "href",
      "/settings/organization/general",
    );
    expect(screen.queryByText("Users")).not.toBeInTheDocument();
    expect(screen.queryByText("Roles")).not.toBeInTheDocument();
    expect(screen.queryByText("Vocabulary")).not.toBeInTheDocument();
    expect(screen.queryByText("Notifications")).not.toBeInTheDocument();
    expect(screen.queryByText("Data export")).not.toBeInTheDocument();
  });

  it("does not eject an authorized admin while permissions are still loading", () => {
    // A cold mount's first render: usePermissions has no initialData, so `can` reads as
    // false for everything before it settles - indistinguishable from "not entitled"
    // unless isLoading is checked too.
    mockUsePermissions.mockReturnValue(
      permissionState(() => false, { isLoading: true }),
    );
    render(
      <ThemeProvider>
        <SidebarProvider>
          <MemoryRouter initialEntries={["/settings/organization/users"]}>
            <Routes>
              {SettingsRoutes({ base: "", edition: openSlots })}
              <Route path="/communities" element={<p>Communities landing</p>} />
            </Routes>
          </MemoryRouter>
        </SidebarProvider>
      </ThemeProvider>,
    );
    expect(screen.queryByText("Communities landing")).not.toBeInTheDocument();
    expect(screen.queryByText("Users page")).not.toBeInTheDocument();
  });
});

describe("the account API keys destination", () => {
  it("routes to it where agents may connect", () => {
    renderRoutes("", "/settings/account/api-keys");
    expect(screen.getByText("API keys page")).toBeInTheDocument();
  });

  it("is not reachable where agents may not connect", () => {
    mockUseAgentAccess.mockReturnValue(NO_AGENT_ACCESS);
    renderRoutes("", "/settings/account/api-keys");
    expect(screen.queryByText("API keys page")).not.toBeInTheDocument();
    expect(
      screen.getByRole("heading", { name: "Account" }),
    ).toBeInTheDocument();
  });

  it("does not eject while the answer is still loading", () => {
    mockUseAgentAccess.mockReturnValue(AGENT_ACCESS_LOADING);
    renderRoutes("", "/settings/account/api-keys");
    // Neither the page nor the redirect's landing spot has rendered: a cold mount waits
    // rather than reading "not answered yet" as "refused".
    expect(screen.queryByText("API keys page")).not.toBeInTheDocument();
    expect(
      screen.queryByRole("heading", { name: "Account" }),
    ).not.toBeInTheDocument();
  });

  it("is not reachable for a hub member nested under their community", () => {
    mockUseCurrentOrganization.mockReturnValue({
      data: { id: "org-1", name: "Acme Hub", kind: "HUB" },
    } as never);
    mockUseAgentAccess.mockReturnValue(NO_AGENT_ACCESS);
    renderRoutes(
      "/communities/:slug",
      "/communities/acme-hub/settings/account/api-keys",
    );
    expect(screen.queryByText("API keys page")).not.toBeInTheDocument();
  });
});
