import { render, screen, within } from "@testing-library/react";
import { MemoryRouter, Route, Routes } from "react-router-dom";
import { describe, expect, it, vi } from "vitest";

import { SidebarProvider } from "@/components/patterns/SidebarProvider";
import { CommunityShell } from "@/features/community/ui/CommunityShell";
import { ThemeProvider } from "@/theme/ThemeProvider";

// Split out of CommunityShell.test.tsx (300-line ceiling): the Learning rail entry.

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

vi.mock("@/features/admin/api/use-current-organization", () => ({
  useCurrentOrganization: vi.fn(() => ({ data: undefined })),
}));

vi.mock("@/features/admin/api/use-permissions", () => ({
  usePermissions: vi.fn(() => ({ can: () => false, isLoading: false })),
}));

// No QueryClientProvider here, so the bell and onboarding queries are stubbed.
vi.mock("@/features/communication/api/use-unread-count", () => ({
  useUnreadCount: () => ({ data: 0 }),
}));

vi.mock("@/features/onboarding/api/use-onboarding", () => ({
  useOnboarding: () => ({ data: undefined }),
  useRecordVisit: () => ({ mutate: vi.fn() }),
  useAssignOnboarding: () => ({ mutate: vi.fn() }),
  useCloseOnboarding: () => ({ mutate: vi.fn() }),
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
    isLoading: false,
  })),
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

function renderShell(location: string) {
  render(
    <ThemeProvider>
      <SidebarProvider>
        <MemoryRouter initialEntries={[location]}>
          <Routes>
            <Route path="/communities/:slug" element={<CommunityShell />}>
              <Route index element={<p>overview-panel</p>} />
              <Route path="learning" element={<p>learning-panel</p>} />
            </Route>
          </Routes>
        </MemoryRouter>
      </SidebarProvider>
    </ThemeProvider>,
  );
}

describe("CommunityShell Learning entry", () => {
  it("lists Learning in the Knowledge group after Expertise", () => {
    // when
    renderShell("/communities/backend-guild");

    // then
    const rail = within(screen.getByTestId("community-rail"));
    const links = rail
      .getAllByRole("link")
      .map((link) => link.textContent.trim());
    expect(links.indexOf("Learning")).toBe(links.indexOf("Expertise") + 1);
    expect(rail.getByRole("link", { name: "Learning" })).toHaveAttribute(
      "href",
      "/communities/backend-guild/learning",
    );
  });

  it("highlights only Learning on the learning route", () => {
    // when
    renderShell("/communities/backend-guild/learning");

    // then
    const rail = within(screen.getByTestId("community-rail"));
    expect(rail.getByRole("link", { name: "Learning" })).toHaveAttribute(
      "aria-current",
      "page",
    );
    expect(rail.getByRole("link", { name: "Expertise" })).not.toHaveAttribute(
      "aria-current",
    );
    expect(screen.getByText("learning-panel")).toBeInTheDocument();
  });
});
