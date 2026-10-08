import { render, screen, within } from "@testing-library/react";
import { MemoryRouter, Routes } from "react-router-dom";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

import { SidebarProvider } from "@/components/patterns/SidebarProvider";
import { useCurrentOrganization } from "@/features/admin/api/use-current-organization";
import { openSlots } from "@/open-slots";
import { SettingsRoutes } from "@/routes/settings-routes";
import {
  signInOrgless,
  signInScopedTo,
  signOutOfTest,
} from "@/test/access-token";
import { NO_AGENT_ACCESS } from "@/test/agent-access";
import { forceMobileViewport, restoreViewport } from "@/test/viewport";
import { ThemeProvider } from "@/theme/ThemeProvider";

vi.mock("@/features/privacy/ui/PrivacySettingsPage", () => ({
  PrivacySettingsPage: ({ personal }: { personal?: boolean }) => (
    <p>{personal === true ? "Personal privacy page" : "Member privacy page"}</p>
  ),
}));
vi.mock("@/features/identity/ui/AccountProfilePage", () => ({
  AccountProfilePage: () => <p>Profile page</p>,
}));
vi.mock("@/features/communication/ui/NotificationPreferencesPage", () => ({
  NotificationPreferencesPage: () => <p>Notification preferences page</p>,
}));
vi.mock("@/features/agent/api/use-personal-access-tokens", () => ({
  useAgentAccess: () => NO_AGENT_ACCESS,
}));
const signinRedirect = vi.fn();
vi.mock("@/auth/session", () => ({
  useAuth: () => ({
    user: { profile: { preferred_username: "alice", sub: "sub-1" } },
    signinRedirect,
    signoutRedirect: vi.fn(),
  }),
}));
const mockUseMyOrganizations = vi.fn();
vi.mock("@/features/admin/api/use-my-organizations", () => ({
  useMyOrganizations: () => mockUseMyOrganizations() as unknown,
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
  usePermissions: () => ({ can: () => true, isLoading: false }),
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

const mockUseCurrentOrganization = vi.mocked(useCurrentOrganization);

const ORGLESS = { data: undefined, isError: false };

function enter(kind: "HUB" | "ENTERPRISE") {
  signInScopedTo("org-1");
  mockUseCurrentOrganization.mockReturnValue({
    data: { id: "org-1", name: "Acme", kind },
  } as never);
}

beforeEach(() => {
  forceMobileViewport();
  sessionStorage.clear();
  signinRedirect.mockClear();
  mockUseMyOrganizations.mockReturnValue({ data: [], isError: false });
  signInOrgless();
  mockUseCurrentOrganization.mockReturnValue(ORGLESS as never);
});

afterEach(() => {
  restoreViewport();
  signOutOfTest();
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

function accountRows(): string[] {
  return within(screen.getByRole("main"))
    .getAllByRole("link")
    .map((link) => link.textContent);
}

describe("account settings for a person with no organization", () => {
  it("lists Privacy and nothing held per organization", () => {
    // when
    renderAt("/settings/account");

    // then
    expect(accountRows()).toEqual(["Privacy"]);
  });

  it("opens the privacy page in its personal form", () => {
    // when
    renderAt("/settings/account/privacy");

    // then
    expect(screen.getByText("Personal privacy page")).toBeInTheDocument();
  });

  it("sends the profile path back to the account overview", () => {
    // when
    renderAt("/settings/account/profile");

    // then
    expect(screen.queryByText("Profile page")).not.toBeInTheDocument();
    expect(
      screen.getByRole("heading", { name: "Account" }),
    ).toBeInTheDocument();
  });

  it("sends the notification preferences path back to the account overview", () => {
    // when
    renderAt("/settings/account/notifications");

    // then
    expect(
      screen.queryByText("Notification preferences page"),
    ).not.toBeInTheDocument();
    expect(
      screen.getByRole("heading", { name: "Account" }),
    ).toBeInTheDocument();
  });

  it("sends the organization surface to account settings", () => {
    // when
    renderAt("/settings/organization");

    // then
    expect(
      screen.getByRole("heading", { name: "Account" }),
    ).toBeInTheDocument();
  });

  it("leads back to the Discover lobby", () => {
    // when
    renderAt("/settings/account");

    // then
    expect(
      within(screen.getByTestId("settings-tabs")).getByRole("link", {
        name: "Discover",
      }),
    ).toHaveAttribute("href", "/discover");
  });

  it("shows no notification bell", () => {
    // when
    renderAt("/settings/account");

    // then
    expect(
      screen.queryByRole("button", { name: /Notifications/ }),
    ).not.toBeInTheDocument();
  });
});

describe("a settings deep link signed in with no organization yet", () => {
  it.each(["/settings/account/profile", "/settings/organization/general"])(
    "sends %s to the account overview without entering an organization",
    (path) => {
      // given
      mockUseMyOrganizations.mockReturnValue({
        data: [{ id: "acme", name: "Acme", kind: "ENTERPRISE" }],
        isError: false,
      });

      // when
      renderAt(path);

      // then
      expect(signinRedirect).not.toHaveBeenCalled();
      expect(
        screen.getByRole("heading", { name: "Account" }),
      ).toBeInTheDocument();
    },
  );
});

describe("account settings for a person in an organization", () => {
  it("lists Privacy for a hub member, beside what the hub holds", () => {
    // given
    enter("HUB");

    // when
    renderAt("/settings/account");

    // then
    expect(accountRows()).toEqual(["Profile", "Notifications", "Privacy"]);
  });

  it("opens the privacy page in its member form for a hub member", () => {
    // given
    enter("HUB");

    // when
    renderAt("/settings/account/privacy");

    // then
    expect(screen.getByText("Member privacy page")).toBeInTheDocument();
  });

  it("offers an enterprise member no Privacy row", () => {
    // given
    enter("ENTERPRISE");

    // when
    renderAt("/settings/account");

    // then
    expect(accountRows()).toEqual(["Profile", "Notifications"]);
  });
});
