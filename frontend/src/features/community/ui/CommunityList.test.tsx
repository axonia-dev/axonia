import { render, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter } from "react-router-dom";
import { beforeEach, describe, expect, it, vi } from "vitest";

import { useCommunities } from "@/features/community/api/use-communities";
import type { CommunityListing } from "@/features/community/domain/community";
import { CommunityList } from "@/features/community/ui/CommunityList";

vi.mock("@/features/community/api/use-communities", () => ({
  useCommunities: vi.fn(),
}));
vi.mock("@/features/community/ui/ExploreJoinButton", () => ({
  ExploreJoinButton: () => <button type="button">Join</button>,
}));

const mockUseCommunities = vi.mocked(useCommunities);

function renderList() {
  return render(
    <MemoryRouter>
      <CommunityList />
    </MemoryRouter>,
  );
}

const community: CommunityListing = {
  id: 1,
  slug: "backend-guild",
  name: "Backend Guild",
  kindId: 2,
  kind: "Guild",
  description: "A guild",
  status: "ACTIVE",
  memberCount: 3,
  role: null,
  createdAt: "2026-05-31T10:00:00Z",
  updatedAt: "2026-05-31T10:00:00Z",
};

function pageOf(content: CommunityListing[], totalPages = 1) {
  return {
    content,
    number: 0,
    totalPages,
    totalElements: content.length,
    size: 20,
  };
}

function answer(
  joined: CommunityListing[],
  explore: CommunityListing[],
  totalPages = 1,
) {
  mockUseCommunities.mockImplementation((params) => {
    const content = params.membership === "JOINED" ? joined : explore;
    return {
      data: pageOf(content, totalPages),
      isLoading: false,
      isError: false,
      errorUpdateCount: 0,
      refetch: vi.fn(),
    } as never;
  });
}

let user: ReturnType<typeof userEvent.setup>;

beforeEach(() => {
  user = userEvent.setup({ delay: null });
  vi.clearAllMocks();
});

describe("CommunityList", () => {
  it("shows skeleton placeholders while loading", () => {
    // given
    mockUseCommunities.mockReturnValue({
      isLoading: true,
      isError: false,
      errorUpdateCount: 0,
      data: undefined,
    } as never);

    // when
    const { container } = renderList();

    // then
    expect(
      container.querySelectorAll("[aria-hidden='true']").length,
    ).toBeGreaterThan(0);
  });

  it("lists joined communities as tiles above the ones to explore", () => {
    // given
    answer(
      [{ ...community, id: 1, slug: "mine", name: "Mine", role: "MEMBER" }],
      [{ ...community, id: 2, slug: "theirs", name: "Theirs", role: null }],
    );

    // when
    renderList();

    // then
    const joined = screen.getByRole("region", { name: "Joined" });
    const explore = screen.getByRole("region", { name: "Explore" });
    expect(
      within(joined).getByRole("link", { name: /Mine/ }),
    ).toBeInTheDocument();
    expect(
      within(explore).getByRole("button", { name: "Join" }),
    ).toBeInTheDocument();
    expect(
      joined.compareDocumentPosition(explore) &
        Node.DOCUMENT_POSITION_FOLLOWING,
    ).toBeTruthy();
  });

  it("renders an explore row with its member count, badge and kind", () => {
    // given
    answer([], [{ ...community, badgeUrl: "https://cdn/pg.png" }]);

    // when
    renderList();

    // then
    expect(
      screen.getByRole("link", { name: /backend guild/i }),
    ).toHaveAttribute("href", "/communities/backend-guild");
    expect(screen.getByText("3 members")).toBeInTheDocument();
    expect(screen.getByText("Guild")).toBeInTheDocument();
    expect(screen.getByRole("img", { name: /backend guild/i })).toHaveAttribute(
      "src",
      "https://cdn/pg.png",
    );
  });

  it("tints an explore row with the community accent, falling back to the kind hue", () => {
    // given
    answer(
      [],
      [
        { ...community, accentColor: "#1aa6a0" },
        { ...community, id: 2, slug: "no-accent", accentColor: null },
      ],
    );

    // when
    renderList();

    // then
    const [accented, fallback] = screen
      .getAllByRole("link")
      .map((link) => link.parentElement);
    expect(accented?.style.getPropertyValue("--k")).toBe("#1aa6a0");
    expect(fallback?.style.getPropertyValue("--k")).not.toBe("");
    expect(fallback?.style.getPropertyValue("--k")).not.toBe("#1aa6a0");
  });

  it("hides the joined section when nothing is joined", () => {
    // given
    answer([], [community]);

    // when
    renderList();

    // then
    expect(screen.queryByRole("region", { name: "Joined" })).toBeNull();
  });

  it("hides the explore section when everything is joined", () => {
    // given
    answer([{ ...community, role: "MEMBER" }], []);

    // when
    renderList();

    // then
    expect(screen.queryByRole("region", { name: "Explore" })).toBeNull();
  });

  it("says there is nothing yet in an organization with no community", () => {
    // given
    answer([], []);

    // when
    renderList();

    // then
    expect(screen.getByText("No communities yet")).toBeInTheDocument();
  });

  it("searches both sections and says when nothing matches", async () => {
    // given
    answer([], []);
    renderList();

    // when
    await user.type(screen.getByLabelText(/filter communities/i), "zzz");

    // then
    expect(mockUseCommunities).toHaveBeenCalledWith(
      expect.objectContaining({ membership: "JOINED", q: "zzz" }),
    );
    expect(mockUseCommunities).toHaveBeenCalledWith(
      expect.objectContaining({ membership: "EXPLORE", q: "zzz" }),
    );
    expect(screen.getByText("No matches")).toBeInTheDocument();
  });

  it("pages each section on its own", async () => {
    // given
    answer([{ ...community, role: "MEMBER" }], [], 2);
    renderList();

    // when
    await user.click(screen.getByRole("button", { name: "Next" }));

    // then
    expect(mockUseCommunities).toHaveBeenCalledWith(
      expect.objectContaining({ membership: "JOINED", page: 1 }),
    );
    expect(mockUseCommunities).toHaveBeenCalledWith(
      expect.objectContaining({ membership: "EXPLORE", page: 0 }),
    );
  });

  it("offers to try again when a section could not load", () => {
    // given
    mockUseCommunities.mockReturnValue({
      data: undefined,
      isLoading: false,
      isError: true,
      isFetching: false,
      errorUpdateCount: 1,
      refetch: vi.fn(),
    } as never);

    // when
    renderList();

    // then
    expect(
      screen.getAllByRole("button", { name: "Try again" }).length,
    ).toBeGreaterThan(0);
  });

  it("shows each section's total beside its label without changing its name", () => {
    // given
    answer(
      [{ ...community, id: 1, slug: "mine", name: "Mine", role: "MEMBER" }],
      [
        { ...community, id: 2, slug: "a", name: "A", role: null },
        { ...community, id: 3, slug: "b", name: "B", role: null },
      ],
    );

    // when
    renderList();

    // then
    const explore = screen.getByRole("region", { name: "Explore" });
    expect(within(explore).getByTestId("section-count")).toHaveTextContent("2");
    expect(
      within(screen.getByRole("region", { name: "Joined" })).getByTestId(
        "section-count",
      ),
    ).toHaveTextContent("1");
  });

  it("gives the search field the full width below sm", () => {
    // given
    answer([], [community]);

    // when
    renderList();

    // then
    const field = screen.getByRole("searchbox").closest("div");
    expect(field?.parentElement?.className ?? "").toContain("sm:max-w-xs");
  });
});
