import { render } from "@testing-library/react";
import { MemoryRouter, Route, Routes } from "react-router-dom";
import { describe, expect, it, vi } from "vitest";

import { SidebarProvider } from "@/components/patterns/SidebarProvider";
import { useCommunity } from "@/features/community/api/use-communities";
import { CommunityShell } from "@/features/community/ui/CommunityShell";
import { kindHue } from "@/lib/kind-hue";
import { ThemeProvider } from "@/theme/ThemeProvider";

vi.mock("@/features/community/api/use-communities", () => ({
  useCommunity: vi.fn(),
  useCommunities: () => ({ data: { content: [] } }),
}));

vi.mock("@/features/admin/api/use-current-organization", () => ({
  useCurrentOrganization: vi.fn(() => ({ data: undefined })),
}));

vi.mock("@/features/admin/api/use-permissions", () => ({
  usePermissions: vi.fn(() => ({ can: () => false, isLoading: false })),
}));

vi.mock("@/features/community/api/use-management-access", () => ({
  useManagementAccess: vi.fn(() => ({
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
  })),
}));

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

function renderShell({
  accentColor,
  kindId,
}: Readonly<{ accentColor: string | null; kindId: number }>) {
  vi.mocked(useCommunity).mockReturnValue({
    isLoading: false,
    isError: false,
    data: {
      id: 1,
      slug: "backend-guild",
      name: "Backend Guild",
      kindId,
      memberCount: 120,
      accentColor,
    },
  } as never);

  return render(
    <ThemeProvider>
      <SidebarProvider>
        <MemoryRouter initialEntries={["/communities/backend-guild"]}>
          <Routes>
            <Route path="/communities/:slug" element={<CommunityShell />}>
              <Route index element={<p>overview-panel</p>} />
            </Route>
          </Routes>
        </MemoryRouter>
      </SidebarProvider>
    </ThemeProvider>,
  );
}

// jsdom cannot resolve an inherited custom property on a descendant node, so these tests can only
// prove the variable is mounted on the scope element itself, not that it propagates further down -
// that propagation is CSS inheritance, exercised for real by the browser rather than by jsdom.
describe("CommunityShell accent", () => {
  it("mounts the community's accent on the scope node", () => {
    const { container } = renderShell({ accentColor: "#f2603c", kindId: 3 });
    const scope = container.querySelector("[data-community-scope]");

    expect(scope).not.toBeNull();
    expect(
      (scope as HTMLElement).style.getPropertyValue("--community-accent"),
    ).toBe("#f2603c");
  });

  it("mounts the kind's hue as a fallback so a community is never uncoloured", () => {
    const { container } = renderShell({ accentColor: null, kindId: 3 });
    const scope = container.querySelector("[data-community-scope]");

    expect(
      (scope as HTMLElement).style.getPropertyValue("--community-accent"),
    ).toBe(kindHue("3"));
  });

  // The scope node above reaches this shell's own tree only. Every overlay react-aria opens is
  // portalled onto the body, outside it, so the accent has to reach the document too - otherwise
  // the onboarding panel, menus and modals all stay the product's pink inside a gold community.
  it("publishes the accent on the document, which is what a portal inherits from", () => {
    renderShell({ accentColor: "#f2603c", kindId: 3 });

    const root = document.documentElement.style;
    expect(root.getPropertyValue("--community-accent")).toBe("#f2603c");
    expect(root.getPropertyValue("--primary")).toBe("#f2603c");
  });

  it("hands the document back when the community is left", () => {
    const { unmount } = renderShell({ accentColor: "#f2603c", kindId: 3 });
    unmount();

    expect(document.documentElement.style.getPropertyValue("--primary")).toBe(
      "",
    );
  });
});
