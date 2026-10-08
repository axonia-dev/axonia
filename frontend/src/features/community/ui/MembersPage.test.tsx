// frontend/src/features/community/ui/MembersPage.test.tsx
import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter, Route, Routes } from "react-router-dom";
import { beforeEach, describe, expect, it, vi } from "vitest";

import { currentUserId } from "@/auth/current-user";
import { useManagementAccess } from "@/features/community/api/use-management-access";
import {
  useChangeRole,
  useMembers,
} from "@/features/community/api/use-membership";
import { MembersPage } from "@/features/community/ui/MembersPage";
import { useUsers } from "@/features/identity/api/use-users";
import { ApiError } from "@/lib/api-client";

vi.mock("@/auth/current-user", () => ({
  currentUserId: vi.fn(() => "nobody"),
}));
vi.mock("@/features/community/api/use-management-access", () => ({
  useManagementAccess: vi.fn(),
}));
vi.mock("@/features/community/api/use-membership", () => ({
  useMembers: vi.fn(),
  useChangeRole: vi.fn(),
}));
vi.mock("@/features/identity/api/use-users", () => ({ useUsers: vi.fn() }));
vi.mock("@/features/community/ui/community-chrome", () => ({
  useCommunityCrumbs: vi.fn(),
}));

const MEMBERS = {
  content: [
    {
      id: 1,
      communityId: 1,
      userId: "alice",
      role: "ORGANIZER",
      joinedAt: "2026-01-01T00:00:00Z",
      updatedAt: "2026-01-01T00:00:00Z",
    },
    {
      id: 2,
      communityId: 1,
      userId: "bob",
      role: "MEMBER",
      joinedAt: "2026-02-01T00:00:00Z",
      updatedAt: "2026-02-01T00:00:00Z",
    },
  ],
};

const USERS = {
  alice: {
    id: "alice",
    displayName: "Alice Wong",
    email: null,
    avatarUrl: null,
    department: null,
    businessUnit: null,
    team: "Payments",
  },
  bob: {
    id: "bob",
    displayName: "Bob Lee",
    email: null,
    avatarUrl: null,
    department: "Design Systems",
    businessUnit: null,
    team: null,
  },
};

function renderPage() {
  render(
    <MemoryRouter
      initialEntries={["/communities/backend-guild/settings/members"]}
    >
      <Routes>
        <Route
          path="/communities/:slug/settings/members"
          element={<MembersPage />}
        />
      </Routes>
    </MemoryRouter>,
  );
}

let user: ReturnType<typeof userEvent.setup>;

beforeEach(() => {
  user = userEvent.setup({ delay: null });
  vi.mocked(currentUserId).mockReturnValue("nobody");
  vi.mocked(useManagementAccess).mockReturnValue({ members: true } as never);
  vi.mocked(useMembers).mockReturnValue({
    data: MEMBERS,
    isPending: false,
    isError: false,
  } as never);
  vi.mocked(useUsers).mockReturnValue({ data: USERS } as never);
  vi.mocked(useChangeRole).mockReturnValue({
    mutate: vi.fn(),
    isPending: false,
    error: null,
  } as never);
});

describe("MembersPage", () => {
  it("blocks users without members access", () => {
    vi.mocked(useManagementAccess).mockReturnValue({ members: false } as never);
    renderPage();
    expect(screen.getByText(/no access/i)).toBeInTheDocument();
    expect(screen.queryByText("Alice Wong")).toBeNull();
  });

  it("renders resolved names with their team or department", () => {
    renderPage();
    expect(screen.getByText("Alice Wong")).toBeInTheDocument();
    expect(screen.getByText(/Payments/)).toBeInTheDocument();
    expect(screen.getByText("Bob Lee")).toBeInTheDocument();
  });

  it("promotes a member through the switch", async () => {
    const mutate = vi.fn();
    vi.mocked(useChangeRole).mockReturnValue({
      mutate,
      isPending: false,
      error: null,
    } as never);
    renderPage();
    await user.click(screen.getByRole("switch", { name: /Bob Lee/ }));
    expect(mutate).toHaveBeenCalledWith(
      { userId: "bob", role: "ORGANIZER" },
      expect.any(Object),
    );
  });

  it("demotes an organizer through the switch", async () => {
    const mutate = vi.fn();
    vi.mocked(useChangeRole).mockReturnValue({
      mutate,
      isPending: false,
      error: null,
    } as never);
    renderPage();
    await user.click(screen.getByRole("switch", { name: /Alice Wong/ }));
    expect(mutate).toHaveBeenCalledWith(
      { userId: "alice", role: "MEMBER" },
      expect.any(Object),
    );
  });

  it("marks the current user", () => {
    vi.mocked(currentUserId).mockReturnValue("alice");
    renderPage();
    expect(screen.getByText("Me")).toBeInTheDocument();
  });

  it("filters by the organizers segment", async () => {
    renderPage();
    await user.click(screen.getByRole("radio", { name: /Organizers · 1/ }));
    expect(screen.getByText("Alice Wong")).toBeInTheDocument();
    expect(screen.queryByText("Bob Lee")).toBeNull();
  });

  it("filters by search query", async () => {
    renderPage();
    await user.type(
      screen.getByRole("searchbox", { name: /search members/i }),
      "design",
    );
    expect(screen.getByText("Bob Lee")).toBeInTheDocument();
    expect(screen.queryByText("Alice Wong")).toBeNull();
  });

  it("surfaces a role-change failure on the affected row", async () => {
    const mutate = vi.fn(
      (
        vars: { userId: string },
        opts?: { onError?: (error: unknown) => void },
      ) => {
        if (vars.userId === "alice") {
          opts?.onError?.(
            new ApiError(409, "demote", "LAST_ORGANIZER_CANNOT_BE_DEMOTED"),
          );
        }
      },
    );
    vi.mocked(useChangeRole).mockReturnValue({
      mutate,
      isPending: false,
      error: null,
    } as never);
    renderPage();
    await user.click(screen.getByRole("switch", { name: /Alice Wong/ }));
    expect(screen.getByRole("alert")).toHaveTextContent(/another organizer/i);
  });

  it("keeps Alice's error message after a different user's success clears the global error", async () => {
    type MutateOpts = {
      onError?: (error: unknown) => void;
      onSuccess?: () => void;
      onSettled?: () => void;
    };
    function fakeChangeRole(vars: { userId: string }, opts?: MutateOpts) {
      if (vars.userId === "alice") {
        opts?.onError?.(
          new ApiError(409, "demote", "LAST_ORGANIZER_CANNOT_BE_DEMOTED"),
        );
      } else if (vars.userId === "bob") {
        opts?.onSuccess?.();
      }
      opts?.onSettled?.();
    }
    const mutate = vi.fn(fakeChangeRole);
    vi.mocked(useChangeRole).mockReturnValue({
      mutate,
      isPending: false,
      error: null,
    } as never);
    renderPage();

    // Alice's role-change fails with a specific 409 code
    await user.click(screen.getByRole("switch", { name: /Alice Wong/ }));
    expect(screen.getByRole("alert")).toHaveTextContent(/another organizer/i);

    // Bob's role-change succeeds — global error is null, but Alice's row must still show her real message
    await user.click(screen.getByRole("switch", { name: /Bob Lee/ }));
    expect(screen.getByRole("alert")).toHaveTextContent(/another organizer/i);
    expect(screen.getByRole("alert")).not.toHaveTextContent(
      /something went wrong/i,
    );
  });

  it("shows a loading hint while members load", () => {
    vi.mocked(useMembers).mockReturnValue({
      data: undefined,
      isPending: true,
      isError: false,
    } as never);
    renderPage();
    expect(screen.getByText(/loading/i)).toBeInTheDocument();
  });

  it("shows an empty hint when there are no members", () => {
    vi.mocked(useMembers).mockReturnValue({
      data: { content: [] },
      isPending: false,
      isError: false,
    } as never);
    vi.mocked(useUsers).mockReturnValue({ data: {} } as never);
    renderPage();
    expect(screen.getByText(/no members yet/i)).toBeInTheDocument();
  });

  it("shows a search-empty hint when the filter matches nobody", async () => {
    renderPage();
    await user.type(
      screen.getByRole("searchbox", { name: /search members/i }),
      "zzzznomatch",
    );
    expect(
      screen.getByText(/no members match this search/i),
    ).toBeInTheDocument();
    expect(screen.queryByText(/no members yet/i)).toBeNull();
  });
});
