import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { Link, MemoryRouter, Route, Routes } from "react-router-dom";
import { afterEach, describe, expect, it, vi } from "vitest";

import { SidebarProvider } from "@/components/patterns/SidebarProvider";
import { useCommunity } from "@/features/community/api/use-communities";
import { CommunityShell } from "@/features/community/ui/CommunityShell";
import {
  useAssignOnboarding,
  useOnboarding,
} from "@/features/onboarding/api/use-onboarding";
import { ThemeProvider } from "@/theme/ThemeProvider";

// Split out of CommunityShell.test.tsx, which sits on the 300-line ceiling: every test there
// stubs `useOnboarding` to `{ data: undefined }`, which keeps the assignment effect's condition
// false throughout - none of them can tell a working guard from a deleted one. These stub the
// one state the guard actually reacts to.
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

vi.mock("@/features/onboarding/api/use-onboarding", () => ({
  useOnboarding: vi.fn(() => ({ data: undefined })),
  useRecordVisit: () => ({ mutate: vi.fn() }),
  useAssignOnboarding: vi.fn(() => ({ mutate: vi.fn() })),
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

function shellTree() {
  return (
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
    </ThemeProvider>
  );
}

function renderShell() {
  return render(shellTree());
}

function switchingShellTree() {
  return (
    <ThemeProvider>
      <SidebarProvider>
        <MemoryRouter initialEntries={["/communities/backend-guild"]}>
          <Routes>
            <Route path="/communities/:slug" element={<CommunityShell />}>
              <Route
                index
                element={<Link to="/communities/frontend-guild">switch</Link>}
              />
            </Route>
          </Routes>
        </MemoryRouter>
      </SidebarProvider>
    </ThemeProvider>
  );
}

describe("CommunityShell assigning onboarding on arrival", () => {
  afterEach(() => {
    vi.mocked(useOnboarding).mockReturnValue({
      data: undefined,
    } as unknown as ReturnType<typeof useOnboarding>);
    vi.mocked(useAssignOnboarding).mockReturnValue({
      mutate: vi.fn(),
    } as unknown as ReturnType<typeof useAssignOnboarding>);
  });

  it("assigns onboarding exactly once for a never-assigned member, even across a re-render", () => {
    const mutate = vi.fn();
    vi.mocked(useOnboarding).mockReturnValue({
      data: { state: "NEVER_ASSIGNED", path: null },
    } as unknown as ReturnType<typeof useOnboarding>);
    vi.mocked(useAssignOnboarding).mockReturnValue({
      mutate,
    } as unknown as ReturnType<typeof useAssignOnboarding>);

    const { rerender } = renderShell();
    expect(mutate).toHaveBeenCalledExactlyOnceWith("backend-guild");

    rerender(shellTree());
    expect(mutate).toHaveBeenCalledExactlyOnceWith("backend-guild");
  });

  it("re-arms after navigating to a different community, since the shell element is reused across a slug change", async () => {
    const mutate = vi.fn();
    vi.mocked(useOnboarding).mockReturnValue({
      data: { state: "NEVER_ASSIGNED", path: null },
    } as unknown as ReturnType<typeof useOnboarding>);
    vi.mocked(useAssignOnboarding).mockReturnValue({
      mutate,
    } as unknown as ReturnType<typeof useAssignOnboarding>);

    render(switchingShellTree());
    expect(mutate).toHaveBeenCalledExactlyOnceWith("backend-guild");

    await userEvent.click(screen.getByRole("link", { name: "switch" }));

    expect(mutate).toHaveBeenCalledTimes(2);
    expect(mutate).toHaveBeenNthCalledWith(2, "frontend-guild");
  });

  it("does not assign onboarding while the community has not resolved yet", () => {
    const mutate = vi.fn();
    vi.mocked(useOnboarding).mockReturnValue({
      data: { state: "NEVER_ASSIGNED", path: null },
    } as unknown as ReturnType<typeof useOnboarding>);
    vi.mocked(useAssignOnboarding).mockReturnValue({
      mutate,
    } as unknown as ReturnType<typeof useAssignOnboarding>);
    vi.mocked(useCommunity).mockReturnValueOnce({
      isLoading: true,
      isError: false,
      data: undefined,
    } as unknown as ReturnType<typeof useCommunity>);

    renderShell();

    expect(mutate).not.toHaveBeenCalled();
  });
});
