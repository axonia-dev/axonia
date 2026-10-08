import { render, screen, within } from "@testing-library/react";
import { NotebookPen, Star } from "lucide-react";
import { MemoryRouter, Routes } from "react-router-dom";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

import { SidebarProvider } from "@/components/patterns/SidebarProvider";
import type { AccountPage, EditionSlots, SettingsSection } from "@/edition";
import { EditionProvider } from "@/edition-context";
import { usePermissions } from "@/features/admin/api/use-permissions";
import { openSlots } from "@/open-slots";
import { SettingsRoutes } from "@/routes/settings-routes";
import { signInScopedToForEachTest } from "@/test/access-token";
import { NO_AGENT_ACCESS } from "@/test/agent-access";
import { permissionState } from "@/test/permissions";
import { forceMobileViewport, restoreViewport } from "@/test/viewport";
import { ThemeProvider } from "@/theme/ThemeProvider";

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

function LedgerPage() {
  return <p>Ledger page</p>;
}

const ledger: SettingsSection = {
  group: {
    label: "Ledger",
    items: [
      {
        path: "ledger",
        label: "Ledger",
        icon: NotebookPen,
        permissions: ["ledger:read"],
      },
    ],
  },
  pages: { ledger: LedgerPage },
};

const offer = vi.hoisted(() => vi.fn());

function ExtraPage() {
  return <p>Extra page</p>;
}

const extraPage: AccountPage = {
  item: { path: "extra", label: "Extra", icon: Star },
  groupLabel: "Extras",
  Page: ExtraPage,
  useOffer: () => offer() as { offered: boolean; isLoading: boolean },
  moved: [["settings/account/old-extra", "settings/account/extra"]],
};

const edition: EditionSlots = {
  ...openSlots,
  settingsSections: [ledger],
  accountPages: [extraPage],
};

const mockUsePermissions = vi.mocked(usePermissions);

signInScopedToForEachTest("org-1");

beforeEach(() => {
  mockUsePermissions.mockReturnValue(
    permissionState((permission) => permission === "ledger:read"),
  );
  offer.mockReturnValue({ offered: true, isLoading: false });
});

afterEach(restoreViewport);

function renderAt(path: string) {
  render(
    <EditionProvider edition={edition}>
      <ThemeProvider>
        <SidebarProvider>
          <MemoryRouter initialEntries={[path]}>
            <Routes>{SettingsRoutes({ base: "", edition })}</Routes>
          </MemoryRouter>
        </SidebarProvider>
      </ThemeProvider>
    </EditionProvider>,
  );
}

describe("an organization settings section an edition contributes", () => {
  it("is served at its path to a caller holding its permission", () => {
    renderAt("/settings/organization/ledger");

    expect(screen.getByText("Ledger page")).toBeInTheDocument();
  });

  it("sends a typed path back to what the rail offers for a caller without it", () => {
    mockUsePermissions.mockReturnValue(permissionState(() => false));

    renderAt("/settings/organization/ledger");

    expect(screen.queryByText("Ledger page")).not.toBeInTheDocument();
    expect(screen.getByText("Tenant settings page")).toBeInTheDocument();
  });

  it("sits on the rail after People & access and before Data", () => {
    mockUsePermissions.mockReturnValue(permissionState(() => true));

    renderAt("/settings/organization/general");

    const rail = within(screen.getByTestId("settings-rail"));
    const names = rail.getAllByRole("link").map((link) => link.textContent);
    expect(names.slice(-3)).toEqual([
      "Directory sync",
      "Ledger",
      "Data export",
    ]);
  });

  it("is not served by an edition that does not contribute it", () => {
    render(
      <ThemeProvider>
        <SidebarProvider>
          <MemoryRouter initialEntries={["/settings/organization/ledger"]}>
            <Routes>{SettingsRoutes({ base: "", edition: openSlots })}</Routes>
          </MemoryRouter>
        </SidebarProvider>
      </ThemeProvider>,
    );

    expect(screen.queryByText("Ledger page")).not.toBeInTheDocument();
  });
});

describe("an account page an edition contributes", () => {
  it("is served where its offer holds, and listed on the rail in a group of its own", () => {
    renderAt("/settings/account/extra");

    expect(screen.getByText("Extra page")).toBeInTheDocument();
    expect(
      within(screen.getByTestId("settings-rail")).getByRole("link", {
        name: "Extra",
      }),
    ).toHaveAttribute("href", "/settings/account/extra");
  });

  it("sends the caller to the account overview, and leaves the rail, where it is not offered", () => {
    // The phone branch: the desktop shell would land the overview on its first section.
    forceMobileViewport();
    offer.mockReturnValue({ offered: false, isLoading: false });

    renderAt("/settings/account/extra");

    expect(screen.queryByText("Extra page")).not.toBeInTheDocument();
    expect(
      screen.getByRole("heading", { name: "Account" }),
    ).toBeInTheDocument();
    expect(
      screen.queryByRole("link", { name: "Extra" }),
    ).not.toBeInTheDocument();
  });

  it("waits rather than ejecting while its offer is loading", () => {
    // The phone branch, where an ejection lands on the overview's own heading.
    forceMobileViewport();
    offer.mockReturnValue({ offered: false, isLoading: true });

    renderAt("/settings/account/extra");

    expect(screen.queryByText("Extra page")).not.toBeInTheDocument();
    expect(
      screen.queryByRole("heading", { name: "Account" }),
    ).not.toBeInTheDocument();
  });

  it("redirects the paths it names as moved", () => {
    renderAt("/settings/account/old-extra");

    expect(screen.getByText("Extra page")).toBeInTheDocument();
  });
});
