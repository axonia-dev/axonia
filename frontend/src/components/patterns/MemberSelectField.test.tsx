import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";

import {
  MemberSelectField,
  type PickedMember,
} from "@/components/patterns/MemberSelectField";
import {
  usePeopleSearch,
  useRosterByName,
} from "@/features/community/api/use-membership";
import type { Membership } from "@/features/community/domain/membership";
import { useUsers } from "@/features/identity/api/use-users";
import type { Page } from "@/lib/page";

vi.mock("@/features/community/api/use-membership", () => ({
  useRosterByName: vi.fn(),
  usePeopleSearch: vi.fn(),
}));
vi.mock("@/features/identity/api/use-users", () => ({ useUsers: vi.fn() }));

const mockUseRosterByName = vi.mocked(useRosterByName);
const mockUsePeopleSearch = vi.mocked(usePeopleSearch);
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
  mockUsePeopleSearch.mockReturnValue({
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
   *  own name search, which reaches the whole organization. */
  it("switches to the server search once text is typed", async () => {
    const user = userEvent.setup({ delay: null });
    mockUsePeopleSearch.mockImplementation(
      (_slug: string, text: string) =>
        ({
          data:
            text === "car"
              ? [{ userId: "u-carl", displayName: "Carl Diaz", member: true }]
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

  it("marks somebody found outside the community", async () => {
    const user = userEvent.setup({ delay: null });
    mockUsePeopleSearch.mockReturnValue({
      data: [
        { userId: "u-ana", displayName: "Ana Bello", member: true },
        { userId: "u-omar", displayName: "Omar Outside", member: false },
      ],
      isFetching: false,
    } as never);
    renderField();

    await user.type(screen.getByRole("combobox"), "o");

    expect(
      await screen.findByRole("option", { name: /Omar Outside/ }),
    ).toHaveTextContent("Not a member");
    expect(
      screen.getByRole("option", { name: /Ana Bello/ }),
    ).not.toHaveTextContent("Not a member");
  });

  it("hands back the id of somebody picked from outside the community", async () => {
    const user = userEvent.setup({ delay: null });
    const onPick = vi.fn();
    mockUsePeopleSearch.mockReturnValue({
      data: [{ userId: "u-omar", displayName: "Omar Outside", member: false }],
      isFetching: false,
    } as never);
    renderField({ onPick });

    await user.type(screen.getByRole("combobox"), "om");
    await user.click(
      await screen.findByRole("option", { name: /Omar Outside/ }),
    );

    expect(onPick).toHaveBeenCalledWith({
      userId: "u-omar",
      displayName: "Omar Outside",
    });
  });

  it("says when nobody is found", async () => {
    const user = userEvent.setup({ delay: null });
    mockUsePeopleSearch.mockReturnValue({
      data: [],
      isFetching: false,
    } as never);
    renderField();

    await user.type(screen.getByRole("combobox"), "zz");

    expect(await screen.findByText("No one found")).toBeInTheDocument();
  });
});
