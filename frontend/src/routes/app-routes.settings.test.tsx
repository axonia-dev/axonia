import { render, screen } from "@testing-library/react";
import { MemoryRouter, Routes } from "react-router-dom";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

import { useIsDelegated } from "@/auth/delegated-session";
import { SidebarProvider } from "@/components/patterns/SidebarProvider";
import { useCurrentOrganization } from "@/features/admin/api/use-current-organization";
import { useAgentAccess } from "@/features/agent/api/use-personal-access-tokens";
import { usePendingReactionCount } from "@/features/recognition/api/use-reactions";
import { openSlots } from "@/open-slots";
import {
  ACCOUNT_LEAF_PATHS,
  MOVED,
  ORGANIZATION_LEAF_PATHS,
  SettingsRoutes,
} from "@/routes/settings-routes";
import { signInScopedToForEachTest } from "@/test/access-token";
import { AGENT_ACCESS } from "@/test/agent-access";
import { permissionState } from "@/test/permissions";
import { forceMobileViewport, restoreViewport } from "@/test/viewport";
import { ThemeProvider } from "@/theme/ThemeProvider";

// Heavy pages: routing is under test here, not their contents.
vi.mock("@/features/agent/ui/ApiKeysView", () => ({
  ApiKeysView: () => <p>API keys page</p>,
}));
vi.mock("@/features/agent/api/use-personal-access-tokens", () => ({
  useAgentAccess: vi.fn(),
}));
vi.mock("@/features/admin/ui/UsersPage", () => ({
  UsersPage: () => <p>Users page</p>,
}));
vi.mock("@/features/admin/ui/RolesPage", () => ({
  RolesPage: () => <p>Roles page</p>,
}));
vi.mock("@/features/community/ui/CommunityKindManager", () => ({
  CommunityKindManager: () => <p>Community types manager</p>,
}));
vi.mock("@/features/knowledge/ui/LibraryTypeManager", () => ({
  LibraryTypeManager: () => <p>Library types manager</p>,
}));
vi.mock("@/features/events/ui/SectionTypeManager", () => ({
  SectionTypeManager: () => <p>Section types manager</p>,
}));
// The vocabulary chrome reads the waiting count for its badge, which is a query.
vi.mock("@/features/recognition/api/use-reactions", () => ({
  usePendingReactionCount: vi.fn(),
}));
vi.mock("@/features/admin/ui/GroupAccessPage", () => ({
  GroupAccessPage: () => <p>Group access page</p>,
}));
vi.mock("@/features/admin/ui/DirectorySyncPage", () => ({
  DirectorySyncPage: () => <p>Directory sync page</p>,
}));
vi.mock("@/features/privacy/ui/DataExportPage", () => ({
  DataExportPage: () => <p>Data export page</p>,
}));
vi.mock("@/features/identity/ui/AccountProfilePage", () => ({
  AccountProfilePage: () => <p>Profile page</p>,
}));
vi.mock("@/features/admin/ui/TenantSettingsPage", () => ({
  TenantSettingsPage: () => <p>Tenant settings page</p>,
}));
vi.mock("@/features/admin/ui/OrganizationNotificationsPage", () => ({
  OrganizationNotificationsPage: () => <p>Organization notifications page</p>,
}));
vi.mock("@/features/communication/ui/NotificationPreferencesPage", () => ({
  NotificationPreferencesPage: () => <p>Notifications page</p>,
}));
vi.mock("@/features/privacy/ui/PrivacySettingsPage", () => ({
  PrivacySettingsPage: () => <p>Privacy page</p>,
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
  usePermissions: () => permissionState(() => true),
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
// SettingsShell's tenant identity chip is stubbed here; SettingsShell.test.tsx covers its rendering.
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
const mockUseIsDelegated = vi.mocked(useIsDelegated);
const mockUseAgentAccess = vi.mocked(useAgentAccess);
const mockUsePendingReactionCount = vi.mocked(usePendingReactionCount);
signInScopedToForEachTest("org-1");

beforeEach(() => {
  forceMobileViewport();
  mockUseCurrentOrganization.mockReturnValue({
    data: { id: "org-1", name: "Default Org", kind: "ENTERPRISE" },
  } as never);
  mockUseIsDelegated.mockReturnValue(false);
  mockUseAgentAccess.mockReturnValue(AGENT_ACCESS);
  mockUsePendingReactionCount.mockReturnValue(0);
});

afterEach(restoreViewport);

// Every row of the redirect table: a row added to MOVED without a matching row here fails the exactness assertion below.
const MOVED_DESTINATIONS: ReadonlyArray<readonly [string, string]> = [
  ["me/api-keys", "API keys page"],
  ["admin/kinds", "Community types manager"],
  ["admin/library-types", "Library types manager"],
  ["admin/section-types", "Section types manager"],
  ["settings/organization/community-types", "Community types manager"],
  ["settings/organization/library-types", "Library types manager"],
  ["settings/organization/section-types", "Section types manager"],
  ["admin/users", "Users page"],
  ["admin/roles", "Roles page"],
  ["admin/group-access", "Group access page"],
  ["admin/directory-sync", "Directory sync page"],
  ["admin/data-export", "Data export page"],
];

const ORGANIZATION_SECTION_DESTINATIONS: ReadonlyArray<
  readonly [string, string]
> = [
  ["general", "Tenant settings page"],
  ["notifications", "Organization notifications page"],
  ["users", "Users page"],
  ["roles", "Roles page"],
  ["group-access", "Group access page"],
  ["directory-sync", "Directory sync page"],
  ["data-export", "Data export page"],
  // The bare leaf redirects to the first permitted tab, same as VocabularyPage's own
  // fallback - mirrors how the account side covers its hand-registered privacy leaf.
  ["vocabulary", "Community types manager"],
];

const ACCOUNT_LEAF_DESTINATIONS: ReadonlyArray<readonly [string, string]> = [
  ["profile", "Profile page"],
  ["api-keys", "API keys page"],
  ["notifications", "Notifications page"],
  ["privacy", "Privacy page"],
];

function sorted(values: ReadonlyArray<string>): string[] {
  return values.slice().sort((a, b) => a.localeCompare(b));
}

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

describe.each([
  ["the top level", "", ""],
  [
    "a hub nested under its community",
    "/communities/:slug",
    "/communities/acme-hub",
  ],
])("settings routes at %s", (_label, base, resolvedBase) => {
  it("redirects every moved path to its new settings section", () => {
    expect(sorted(MOVED_DESTINATIONS.map(([from]) => from))).toEqual(
      sorted(MOVED.map(([from]) => from)),
    );
    for (const [from, expectedText] of MOVED_DESTINATIONS) {
      const { unmount } = renderRoutes(base, `${resolvedBase}/${from}`);
      expect(screen.getByText(expectedText)).toBeInTheDocument();
      unmount();
    }
  });

  it("serves the account leaf pages at their new paths", () => {
    // Self-policing: a leaf added to the real catalogue without a matching row here
    // fails this assertion instead of leaving the loop below silently incomplete.
    expect(sorted(ACCOUNT_LEAF_DESTINATIONS.map(([path]) => path))).toEqual(
      sorted(ACCOUNT_LEAF_PATHS),
    );
    mockUseCurrentOrganization.mockReturnValue({
      data: { id: "org-1", name: "Acme Hub", kind: "HUB" },
    } as never);
    for (const [path, expectedText] of ACCOUNT_LEAF_DESTINATIONS) {
      const { unmount } = renderRoutes(
        base,
        `${resolvedBase}/settings/account/${path}`,
      );
      expect(screen.getByText(expectedText)).toBeInTheDocument();
      unmount();
    }
  });

  it("serves every organization section at its new path", () => {
    // Self-policing: a leaf added to the real catalogue (including the hand-registered
    // vocabulary one) without a matching row here fails this assertion instead of leaving
    // the loop below silently incomplete.
    expect(
      sorted(ORGANIZATION_SECTION_DESTINATIONS.map(([path]) => path)),
    ).toEqual(sorted(ORGANIZATION_LEAF_PATHS));
    for (const [path, expectedText] of ORGANIZATION_SECTION_DESTINATIONS) {
      const { unmount } = renderRoutes(
        base,
        `${resolvedBase}/settings/organization/${path}`,
      );
      expect(screen.getByText(expectedText)).toBeInTheDocument();
      unmount();
    }
    renderRoutes(base, `${resolvedBase}/settings/organization/roles/7`);
    expect(screen.getByText("Roles page")).toBeInTheDocument();
  });

  // The rail already drops the row for a hub, but the URL outlives the rail: a bookmark, the
  // /admin/group-access redirect, or a hand-typed address would otherwise mount a page whose
  // only endpoint now 404s. The enterprise half is the loop above, which serves this very path.
  it("redirects a hub away from group access rather than serving a dead page", () => {
    mockUseCurrentOrganization.mockReturnValue({
      data: { id: "org-1", name: "Acme Hub", kind: "HUB" },
    } as never);

    renderRoutes(base, `${resolvedBase}/settings/organization/group-access`);

    expect(screen.queryByText("Group access page")).not.toBeInTheDocument();
    expect(
      screen.getByRole("heading", { name: "Community" }),
    ).toBeInTheDocument();
  });

  it("titles the overview differently from the shell chrome above it", () => {
    renderRoutes(base, `${resolvedBase}/settings/account`);
    expect(
      screen.getByRole("heading", { name: "Account" }),
    ).toBeInTheDocument();
    expect(screen.getAllByText("Account settings").length).toBeGreaterThan(0);
  });
});
