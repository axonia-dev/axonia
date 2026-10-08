import { render, screen, within } from "@testing-library/react";
import { MemoryRouter, Route, Routes } from "react-router-dom";
import { describe, expect, it, vi } from "vitest";

import { SidebarProvider } from "@/components/patterns/SidebarProvider";
import { useCommunityCrumbs } from "@/features/community/ui/community-chrome";
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

vi.mock("@/features/admin/api/use-current-organization", () => ({
  useCurrentOrganization: vi.fn(() => ({ data: undefined })),
}));

vi.mock("@/features/admin/api/use-permissions", () => ({
  usePermissions: vi.fn(() => ({ can: () => false, isLoading: false })),
}));

vi.mock("@/features/communication/api/use-unread-count", () => ({
  useUnreadCount: () => ({ data: 4 }),
}));

// The onboarding affordance in both top bars; no QueryClientProvider here, so its query is stubbed.
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

// Mirrors SessionDetailPage/OccurrencePreviewPage: the page registers its own
// crumb once loaded. Without it, this stub's crumb would be indistinguishable
// from a still-loading page, which Breadcrumb (correctly, per the hub-org
// test) renders as the trail's un-linked current step rather than as a link -
// exactly the ambiguity a parent crumb cannot resolve on its own.
function Panel() {
  useCommunityCrumbs([{ label: "Weekly sync" }]);
  return <p>panel</p>;
}

function renderAt(path: string, routePath: string) {
  render(
    <ThemeProvider>
      <SidebarProvider>
        <MemoryRouter initialEntries={[path]}>
          <Routes>
            <Route path="/communities/:slug" element={<CommunityShell />}>
              <Route path={routePath} element={<Panel />} />
            </Route>
          </Routes>
        </MemoryRouter>
      </SidebarProvider>
    </ThemeProvider>,
  );
}

// Only SessionDetailPage.tsx:92 reaches this: on its loading and not-found
// branches it registers no crumb at all. OccurrencePreviewPage.tsx:51 always
// registers one, defaulting to the literal "Session" - this stub mirrors the
// SessionDetailPage case, the one that actually produces the empty-crumb state.
function renderAtBeforeCrumb(path: string, routePath: string) {
  render(
    <ThemeProvider>
      <SidebarProvider>
        <MemoryRouter initialEntries={[path]}>
          <Routes>
            <Route path="/communities/:slug" element={<CommunityShell />}>
              <Route path={routePath} element={<p>panel</p>} />
            </Route>
          </Routes>
        </MemoryRouter>
      </SidebarProvider>
    </ThemeProvider>,
  );
}

describe("CommunityShell section ownership", () => {
  // Both halves of one declaration, asserted together on purpose: a fix that
  // lands only the crumb leaves the rail saying the session is somewhere else,
  // which is the defect being closed here.
  it("names Schedule as a session's parent and keeps it lit", () => {
    renderAt("/communities/backend-guild/sessions/41", "sessions/:sessionId");
    const crumbs = within(
      screen.getByRole("navigation", { name: "Breadcrumb" }),
    );
    expect(crumbs.getByRole("link", { name: "Schedule" })).toHaveAttribute(
      "href",
      "/communities/backend-guild/schedule",
    );
    const rail = within(screen.getByTestId("community-rail"));
    expect(rail.getByRole("link", { name: "Schedule" })).toHaveAttribute(
      "aria-current",
      "page",
    );
  });

  it("does the same for an occurrence preview", () => {
    renderAt(
      "/communities/backend-guild/series/7/occurrences/2026-08-13",
      "series/:seriesId/occurrences/:date",
    );
    const crumbs = within(
      screen.getByRole("navigation", { name: "Breadcrumb" }),
    );
    expect(crumbs.getByRole("link", { name: "Schedule" })).toBeInTheDocument();
  });

  // A section page is its own destination; a self-referential crumb would be noise.
  it("adds no parent crumb on the schedule itself", () => {
    renderAt("/communities/backend-guild/schedule", "schedule");
    const crumbs = within(
      screen.getByRole("navigation", { name: "Breadcrumb" }),
    );
    expect(crumbs.queryByRole("link", { name: "Schedule" })).toBeNull();
  });

  // Before the page has a crumb of its own (loading, or SessionDetailPage's
  // not-found branch), a bare Schedule parent would land as the trail's last
  // item and read as the current page - falsely claiming the error page is
  // Schedule. The pre-task trail (community name linked, no Schedule crumb)
  // survives instead, and the rail still carries the way back.
  it("does not claim to be Schedule before the page has its own crumb", () => {
    renderAtBeforeCrumb(
      "/communities/backend-guild/sessions/41",
      "sessions/:sessionId",
    );
    const crumbs = within(
      screen.getByRole("navigation", { name: "Breadcrumb" }),
    );
    for (const el of crumbs.queryAllByText("Schedule")) {
      expect(el).not.toHaveAttribute("aria-current", "page");
    }
    expect(crumbs.getByRole("link", { name: "Communities" })).toHaveAttribute(
      "href",
      "/communities",
    );
    const rail = within(screen.getByTestId("community-rail"));
    expect(rail.getByRole("link", { name: "Schedule" })).toHaveAttribute(
      "aria-current",
      "page",
    );
  });
});
