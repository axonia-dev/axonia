import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter, Route, Routes } from "react-router-dom";
import { describe, expect, it, vi } from "vitest";

import { SidebarProvider } from "@/components/patterns/SidebarProvider";
import { useCommunity } from "@/features/community/api/use-communities";
import { CommunityShell } from "@/features/community/ui/CommunityShell";
import { ThemeProvider } from "@/theme/ThemeProvider";

// Split out of CommunityShell.test.tsx (300-line ceiling): the loading/error states
// are a distinct concern from that file's rail/tab-bar coverage.

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

// No QueryClientProvider here, so the onboarding queries are stubbed.
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

function renderShell() {
  render(
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

describe("CommunityShell loading/error states", () => {
  it("shows a loading state while the community is fetching", () => {
    vi.mocked(useCommunity).mockReturnValue({
      isLoading: true,
      isError: false,
      data: undefined,
    } as never);
    renderShell();
    expect(screen.getByText("Loading…")).toBeInTheDocument();
  });

  it("shows an error state when the community fails to load", () => {
    vi.mocked(useCommunity).mockReturnValue(failed() as never);
    renderShell();
    expect(screen.getByRole("alert")).toHaveTextContent(
      "Could not load this community.",
    );
  });

  it("asks for the community again when told to", async () => {
    // given
    const refetch = vi.fn();
    vi.mocked(useCommunity).mockReturnValue(failed({ refetch }) as never);
    renderShell();

    // when
    await userEvent.click(screen.getByRole("button", { name: "Try again" }));

    // then
    expect(refetch).toHaveBeenCalledOnce();
  });

  it("keeps the failure on screen while it asks again", () => {
    // given
    vi.mocked(useCommunity).mockReturnValue(
      failed({ isLoading: true, isError: false, isFetching: true }) as never,
    );

    // when
    renderShell();

    // then
    expect(screen.getByRole("alert")).toHaveTextContent(
      "Could not load this community.",
    );
    expect(screen.getByRole("button", { name: "Try again" })).toHaveAttribute(
      "data-pending",
      "true",
    );
    expect(screen.queryByText("Loading…")).not.toBeInTheDocument();
  });
});

/** A community read that has failed at least once and holds nothing to show. */
function failed(overrides: Record<string, unknown> = {}) {
  return {
    isLoading: false,
    isError: true,
    isFetching: false,
    errorUpdateCount: 1,
    data: undefined,
    refetch: vi.fn(),
    ...overrides,
  };
}
