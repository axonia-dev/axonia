import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";

import {
  MemberSelectField,
  type PickedMember,
} from "@/components/patterns/MemberSelectField";
import {
  useMemberSearch,
  useRosterByName,
} from "@/features/community/api/use-membership";
import type { Membership } from "@/features/community/domain/membership";
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
  { userId: "u-ana", displayName: "Ana Bello" },
  { userId: "u-bea", displayName: "Bea Ng" },
  { userId: "u-carl", displayName: "Carl Diaz" },
];

function rosterMembership(userId: string): Membership {
  return {
    id: 1,
    communityId: 1,
    userId,
    role: "MEMBER",
    joinedAt: "2026-01-01T00:00:00Z",
    updatedAt: "2026-01-01T00:00:00Z",
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
      {
        id: userId,
        displayName,
        avatarUrl: `https://example.test/${userId}.png`,
      },
    ]),
  );
}

function renderField(overrides: Partial<PickedFieldProps> = {}) {
  const props: PickedFieldProps = {
    slug: "guild",
    label: "Assignee",
    picked: null,
    onPick: vi.fn(),
    ...overrides,
  };
  render(<MemberSelectField {...props} />);
  return props;
}

interface PickedFieldProps {
  slug: string;
  label: string;
  picked: PickedMember | null;
  onPick: (member: PickedMember) => void;
  onClear?: () => void;
}

beforeEach(() => {
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

describe("MemberSelectField", () => {
  it("opens on the roster in name order, with a face on each option", async () => {
    const user = userEvent.setup({ delay: null });
    renderField();

    await user.click(screen.getByRole("combobox"));

    expect(screen.getAllByRole("option").map((o) => o.textContent)).toEqual([
      "Ana Bello",
      "Bea Ng",
      "Carl Diaz",
    ]);
    expect(screen.getAllByRole("img", { hidden: true })).toHaveLength(3);
  });

  /** The roster listing is ordered but capped per page; a typed query is answered by the server's
   *  own name search, which is scoped to the roster. */
  it("switches to the server search once text is typed", async () => {
    const user = userEvent.setup({ delay: null });
    mockUseMemberSearch.mockImplementation(
      (_slug: string, text: string) =>
        ({
          data:
            text === "car"
              ? [{ userId: "u-carl", displayName: "Carl Diaz" }]
              : [],
          isFetching: false,
        }) as never,
    );
    renderField();

    await user.type(screen.getByRole("combobox"), "car");

    await waitFor(() =>
      expect(screen.getAllByRole("option").map((o) => o.textContent)).toEqual([
        "Carl Diaz",
      ]),
    );
  });

  it("reports the picked member's id, never the typed text", async () => {
    const user = userEvent.setup({ delay: null });
    const onPick = vi.fn();
    renderField({ onPick });

    await user.click(screen.getByRole("combobox"));
    await user.click(screen.getByRole("option", { name: "Bea Ng" }));

    expect(onPick).toHaveBeenCalledWith({
      userId: "u-bea",
      displayName: "Bea Ng",
    });
  });
});
