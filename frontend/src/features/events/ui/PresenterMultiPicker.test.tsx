import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";

import {
  useMemberSearch,
  useRosterByName,
} from "@/features/community/api/use-membership";
import type { Membership } from "@/features/community/domain/membership";
import { PresenterMultiPicker } from "@/features/events/ui/PresenterMultiPicker";
import { useUsers } from "@/features/identity/api/use-users";
import type { Page } from "@/lib/page";

vi.mock("@/features/community/api/use-membership", () => ({
  useRosterByName: vi.fn(),
  useMemberSearch: vi.fn(),
}));
vi.mock("@/features/identity/api/use-users", () => ({ useUsers: vi.fn() }));

const mockUseRosterByName = vi.mocked(useRosterByName);
const mockUseMemberSearch = vi.mocked(useMemberSearch);
const mockUseUsers = vi.mocked(useUsers);

const roster = [
  { userId: "mia", displayName: "Mia Eriksson" },
  { userId: "bob", displayName: "Bob Brown" },
  { userId: "u-bea", displayName: "Bea Ng" },
];

function rosterMembership(userId: string): Membership {
  return {
    id: 1,
    communityId: 1,
    userId,
    role: "MEMBER",
    joinedAt: "2024-01-01T00:00:00Z",
    updatedAt: "2024-01-01T00:00:00Z",
  };
}

function rosterPage(): Page<Membership> {
  return {
    content: roster.map((m) => rosterMembership(m.userId)),
    totalElements: roster.length,
    totalPages: 1,
    number: 0,
    size: 25,
  };
}

function directory() {
  return Object.fromEntries(
    roster.map(({ userId, displayName }) => [
      userId,
      { id: userId, displayName, email: null },
    ]),
  );
}

let user: ReturnType<typeof userEvent.setup>;

beforeEach(() => {
  user = userEvent.setup({ delay: null });
  vi.clearAllMocks();
  mockUseRosterByName.mockReturnValue({
    data: { pages: [rosterPage()] },
    hasNextPage: false,
    fetchNextPage: vi.fn(),
  } as never);
  mockUseMemberSearch.mockReturnValue({
    data: undefined,
    isFetching: false,
  } as never);
  mockUseUsers.mockReturnValue({ data: directory() } as never);
});

describe("PresenterMultiPicker", () => {
  it("reports a picked member's userId, appended to the list", async () => {
    const onChange = vi.fn();
    render(
      <PresenterMultiPicker slug="guild" value={[]} onChange={onChange} />,
    );
    await user.click(screen.getByRole("combobox"));
    await user.click(screen.getByRole("option", { name: "Mia Eriksson" }));

    expect(onChange).toHaveBeenCalledWith(["mia"]);
  });

  it("removes a chip", async () => {
    const onChange = vi.fn();
    render(
      <PresenterMultiPicker slug="guild" value={["mia"]} onChange={onChange} />,
    );
    await user.click(
      screen.getByRole("button", { name: "Remove Mia Eriksson" }),
    );

    expect(onChange).toHaveBeenCalledWith([]);
  });

  /** The field has no way to exclude an id it has already offered, so this is the one place that
   *  decides an already-picked member is a no-op rather than a duplicate. */
  it("ignores a member who is already on the list", async () => {
    const onChange = vi.fn();
    render(
      <PresenterMultiPicker
        slug="backend-guild"
        value={["u-bea"]}
        onChange={onChange}
      />,
    );

    await user.click(screen.getByRole("combobox"));
    await user.click(screen.getByRole("option", { name: "Bea Ng" }));

    expect(onChange).not.toHaveBeenCalled();
  });
});
