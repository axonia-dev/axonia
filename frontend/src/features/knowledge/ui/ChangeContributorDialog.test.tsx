import { act, render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

import {
  useMemberSearch,
  useRosterByName,
} from "@/features/community/api/use-membership";
import { useChangeContributor } from "@/features/knowledge/api/use-artifacts";
import type { Artifact } from "@/features/knowledge/domain/artifact";
import { ChangeContributorDialog } from "@/features/knowledge/ui/ChangeContributorDialog";
import { ApiError } from "@/lib/api-client";

vi.mock("@/features/knowledge/api/use-artifacts", () => ({
  useChangeContributor: vi.fn(),
}));
vi.mock("@/features/identity/api/use-users", () => ({
  useUsers: () => ({ data: { olive: { displayName: "Olive Organizer" } } }),
}));
vi.mock("@/features/community/api/use-membership", () => ({
  useRosterByName: vi.fn(),
  useMemberSearch: vi.fn(),
}));

const mockChange = vi.mocked(useChangeContributor);
const mockRosterByName = vi.mocked(useRosterByName);
const mockMemberSearch = vi.mocked(useMemberSearch);

const artifact: Artifact = {
  id: 1,
  communityId: 2,
  typeId: 3,
  type: "Recording",
  title: "Kafka in production",
  description: "",
  url: "https://example.com/kafka",
  tags: [],
  contributorUserId: "olive",
  loveCount: 0,
  provenance: null,
  status: "ACTIVE",
  createdAt: "x",
  updatedAt: "x",
};

let user: ReturnType<typeof userEvent.setup>;

beforeEach(() => {
  vi.clearAllMocks();
  user = userEvent.setup({ delay: null });
  vi.useFakeTimers({ shouldAdvanceTime: true });
  mockChange.mockReturnValue({
    mutate: vi.fn(),
    isPending: false,
    isError: false,
    error: null,
  } as never);
  mockMemberSearch.mockReturnValue({ data: [], isFetching: false } as never);
  mockRosterByName.mockReturnValue({
    data: { pages: [] },
    hasNextPage: false,
    fetchNextPage: vi.fn(),
  } as never);
});

afterEach(() => {
  vi.useRealTimers();
});

function renderDialog(onOpenChange = vi.fn()) {
  render(
    <ChangeContributorDialog
      artifact={artifact}
      slug="backend-guild"
      isOpen
      onOpenChange={onOpenChange}
    />,
  );
  return onOpenChange;
}

/** Types a name into the real Contributor picker, lets the debounce settle, and picks the
 *  given match from the resulting list. */
async function pick(
  query: string,
  member: { userId: string; displayName: string },
) {
  mockMemberSearch.mockReturnValue({
    data: [member],
    isFetching: false,
  } as never);
  const combobox = screen.getByRole("combobox", { name: "Contributor" });
  await user.click(combobox);
  await user.clear(combobox);
  await user.type(combobox, query);
  act(() => {
    vi.advanceTimersByTime(300);
  });
  await user.click(
    await screen.findByRole("option", { name: member.displayName }),
  );
}

describe("ChangeContributorDialog", () => {
  it("names who the artifact credits now", () => {
    // when
    renderDialog();

    // then
    expect(
      screen.getByText("Credited to Olive Organizer."),
    ).toBeInTheDocument();
  });

  it("labels the picker Contributor, the field's established name", () => {
    // when
    renderDialog();

    // then
    expect(
      screen.getByRole("combobox", { name: "Contributor" }),
    ).toBeInTheDocument();
  });

  it("hints that the field searches the community's members", () => {
    // when
    renderDialog();

    // then
    expect(
      screen.getByRole("combobox", { name: "Contributor" }),
    ).toHaveAttribute("placeholder", "Search members");
  });

  it("keeps Change disabled with nobody picked", () => {
    // when
    renderDialog();

    // then
    expect(screen.getByRole("button", { name: "Change" })).toBeDisabled();
  });

  it("keeps Change disabled when the pick names the current contributor", async () => {
    // given
    renderDialog();

    // when
    await pick("Olive", { userId: "olive", displayName: "Olive Organizer" });

    // then
    expect(screen.getByRole("button", { name: "Change" })).toBeDisabled();
  });

  it("enables Change once somebody else is picked", async () => {
    // given
    renderDialog();

    // when
    await pick("Bob", { userId: "bob", displayName: "Bob Martin" });

    // then
    expect(screen.getByRole("button", { name: "Change" })).toBeEnabled();
  });

  it("reopens the list for a second pick, without leaving the field", async () => {
    // given - a first pick that changes nothing
    renderDialog();
    await pick("Olive", { userId: "olive", displayName: "Olive Organizer" });
    expect(screen.getByRole("button", { name: "Change" })).toBeDisabled();

    // when - retyping in the same field picks somebody else
    await pick("Bob", { userId: "bob", displayName: "Bob Martin" });

    // then
    expect(screen.getByRole("button", { name: "Change" })).toBeEnabled();
  });

  it("disables Change while the mutation is pending", async () => {
    // given
    mockChange.mockReturnValue({
      mutate: vi.fn(),
      isPending: true,
      isError: false,
      error: null,
    } as never);
    renderDialog();

    // when
    await pick("Bob", { userId: "bob", displayName: "Bob Martin" });

    // then
    expect(screen.getByRole("button", { name: "Change" })).toBeDisabled();
  });

  it("sends the picked member's id", async () => {
    // given
    const mutate = vi.fn();
    mockChange.mockReturnValue({
      mutate,
      isPending: false,
      isError: false,
      error: null,
    } as never);
    renderDialog();

    // when
    await pick("Bob", { userId: "bob", displayName: "Bob Martin" });
    await user.click(screen.getByRole("button", { name: "Change" }));

    // then
    expect(mutate.mock.calls[0]?.[0]).toEqual({ id: 1, contributor: "bob" });
  });

  it("asks to close when Cancel is pressed", async () => {
    // given
    const onOpenChange = renderDialog();

    // when
    await user.click(screen.getByRole("button", { name: "Cancel" }));

    // then
    expect(onOpenChange).toHaveBeenCalledWith(false);
  });

  it("says so when the person is not a member", () => {
    // given
    mockChange.mockReturnValue({
      mutate: vi.fn(),
      isPending: false,
      isError: true,
      error: new ApiError(409, "detail", "CONTRIBUTOR_NOT_FOUND"),
    } as never);

    // when
    renderDialog();

    // then
    expect(screen.getByRole("alert")).toHaveTextContent(
      "That person is not a member of this community.",
    );
  });

  it("says so when the axis was revoked mid-session", () => {
    // given
    mockChange.mockReturnValue({
      mutate: vi.fn(),
      isPending: false,
      isError: true,
      error: new ApiError(403, "detail"),
    } as never);

    // when
    renderDialog();

    // then
    expect(screen.getByRole("alert")).toHaveTextContent(
      "Crediting contributions is no longer open here.",
    );
  });
});
