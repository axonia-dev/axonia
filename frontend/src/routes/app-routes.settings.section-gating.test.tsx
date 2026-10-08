import { render, screen } from "@testing-library/react";
import { MemoryRouter, Routes } from "react-router-dom";
import { beforeEach, describe, expect, it, vi } from "vitest";

import { SidebarProvider } from "@/components/patterns/SidebarProvider";
import { usePermissions } from "@/features/admin/api/use-permissions";
import { openSlots } from "@/open-slots";
import { SettingsRoutes } from "@/routes/settings-routes";
import { signInScopedToForEachTest } from "@/test/access-token";
import { NO_AGENT_ACCESS } from "@/test/agent-access";
import { permissionState } from "@/test/permissions";
import { ThemeProvider } from "@/theme/ThemeProvider";

vi.mock("@/features/admin/ui/UsersPage", () => ({
  UsersPage: () => <p>Users page</p>,
}));
vi.mock("@/features/admin/ui/TenantSettingsPage", () => ({
  TenantSettingsPage: () => <p>Tenant settings page</p>,
}));
vi.mock("@/features/admin/api/use-permissions", () => ({
  usePermissions: vi.fn(),
}));
vi.mock("@/features/admin/api/use-current-organization", () => ({
  useCurrentOrganization: () => ({
    data: { id: "org-1", name: "Acme", kind: "ENTERPRISE" },
  }),
}));
vi.mock("@/features/agent/api/use-personal-access-tokens", () => ({
  useAgentAccess: () => NO_AGENT_ACCESS,
}));
vi.mock("@/auth/session", () => ({
  useAuth: () => ({
    user: { profile: { preferred_username: "alice", sub: "sub-1" } },
    signoutRedirect: vi.fn(),
  }),
}));
vi.mock("@/features/identity/api/use-me", () => ({
  useMe: () => ({ data: { id: "sub-1", displayName: "Alice", email: null } }),
}));
vi.mock("@/features/communication/api/use-unread-count", () => ({
  useUnreadCount: () => ({ data: 0 }),
}));
vi.mock("@/features/onboarding/api/use-onboarding", () => ({
  useOnboarding: () => ({ data: undefined }),
  useRecordVisit: () => ({ mutate: vi.fn() }),
  useCloseOnboarding: () => ({ mutate: vi.fn() }),
}));
vi.mock("@/features/admin/ui/OrganizationSwitcher", () => ({
  OrganizationSwitcher: () => null,
}));
vi.mock("@/features/community/api/use-communities", () => ({
  useCommunities: () => ({ data: undefined }),
}));
vi.mock("@/auth/delegated-session", () => ({
  useIsDelegated: () => false,
}));

const mockUsePermissions = vi.mocked(usePermissions);

signInScopedToForEachTest("org-1");

beforeEach(() => {
  mockUsePermissions.mockReturnValue(permissionState(() => false));
});

function renderAt(path: string) {
  render(
    <ThemeProvider>
      <SidebarProvider>
        <MemoryRouter initialEntries={[path]}>
          <Routes>{SettingsRoutes({ base: "", edition: openSlots })}</Routes>
        </MemoryRouter>
      </SidebarProvider>
    </ThemeProvider>,
  );
}

/**
 * The rail offers a member only what their permissions reach, but a typed or bookmarked path
 * reached the page anyway and filled it with refusals. A section the rail would not offer is
 * not a destination either.
 */
describe("an organization settings section the caller may not see", () => {
  it("sends a typed path back to what the rail offers", () => {
    // when
    renderAt("/settings/organization/users");

    // then
    expect(screen.queryByText("Users page")).not.toBeInTheDocument();
    expect(screen.getByText("Tenant settings page")).toBeInTheDocument();
  });

  it("opens the same path for a caller holding its permission", () => {
    // given
    mockUsePermissions.mockReturnValue(permissionState(() => true));

    // when
    renderAt("/settings/organization/users");

    // then
    expect(screen.getByText("Users page")).toBeInTheDocument();
  });

  it("opens an ungated section for anyone", () => {
    // when
    renderAt("/settings/organization/general");

    // then
    expect(screen.getByText("Tenant settings page")).toBeInTheDocument();
  });

  it("keeps a typed path when the permissions read failed, so the section can say so", () => {
    // given
    mockUsePermissions.mockReturnValue(
      permissionState(() => false, { isError: true }),
    );

    // when
    renderAt("/settings/organization/users");

    // then
    expect(screen.getByText("Users page")).toBeInTheDocument();
  });

  it("shows no section and no redirect while permissions load", () => {
    // given
    mockUsePermissions.mockReturnValue(
      permissionState(() => false, { isLoading: true }),
    );

    // when
    renderAt("/settings/organization/users");

    // then
    expect(screen.queryByText("Users page")).not.toBeInTheDocument();
    expect(screen.queryByText("Tenant settings page")).not.toBeInTheDocument();
  });
});
