import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter } from "react-router-dom";
import { beforeEach, describe, expect, it, vi } from "vitest";

import { useCommunities } from "@/features/community/api/use-communities";
import { CommunityList } from "@/features/community/ui/CommunityList";

vi.mock("@/features/community/api/use-communities", () => ({
  useCommunities: vi.fn(),
}));

const mockUseCommunities = vi.mocked(useCommunities);

function renderList() {
  return render(
    <MemoryRouter>
      <CommunityList />
    </MemoryRouter>,
  );
}

const community = {
  id: 1,
  slug: "backend-guild",
  name: "Backend Guild",
  kindId: 2,
  kind: "Guild",
  description: "A guild",
  status: "ACTIVE" as const,
  memberCount: 3,
  createdAt: "2026-05-31T10:00:00Z",
  updatedAt: "2026-05-31T10:00:00Z",
};

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
      data: undefined,
    } as never);

    // when
    const { container } = renderList();

    // then
    expect(
      container.querySelectorAll("[aria-hidden='true']").length,
    ).toBeGreaterThan(0);
  });

  it("shows an error message", () => {
    // given
    mockUseCommunities.mockReturnValue({
      isLoading: false,
      isError: true,
      data: undefined,
    } as never);

    // when
    renderList();

    // then
    expect(screen.getByRole("alert")).toBeInTheDocument();
  });

  it("shows an empty state", () => {
    // given
    mockUseCommunities.mockReturnValue({
      isLoading: false,
      isError: false,
      data: {
        content: [],
        totalElements: 0,
        totalPages: 0,
        number: 0,
        size: 20,
      },
    } as never);

    // when
    renderList();

    // then
    expect(screen.getByText(/no communities/i)).toBeInTheDocument();
  });

  it("renders each community with its member count", () => {
    // given
    mockUseCommunities.mockReturnValue({
      isLoading: false,
      isError: false,
      data: {
        content: [community],
        totalElements: 1,
        totalPages: 1,
        number: 0,
        size: 20,
      },
    } as never);

    // when
    renderList();

    // then
    expect(
      screen.getByRole("link", { name: /backend guild/i }),
    ).toHaveAttribute("href", "/communities/backend-guild");
    expect(screen.getByText(/3 members/i)).toBeInTheDocument();
  });

  it("counts a sole member in the singular", () => {
    // given
    mockUseCommunities.mockReturnValue({
      isLoading: false,
      isError: false,
      data: {
        content: [{ ...community, memberCount: 1 }],
        totalElements: 1,
        totalPages: 1,
        number: 0,
        size: 20,
      },
    } as never);

    // when
    renderList();

    // then
    expect(screen.getByText("1 member")).toBeInTheDocument();
  });

  it("uses the community accent colour for its row, falling back to the kind hue", () => {
    // given
    mockUseCommunities.mockReturnValue({
      isLoading: false,
      isError: false,
      data: {
        content: [
          { ...community, accentColor: "#1aa6a0" },
          { ...community, id: 2, slug: "no-accent", accentColor: null },
        ],
        totalElements: 2,
        totalPages: 1,
        number: 0,
        size: 20,
      },
    } as never);

    // when
    renderList();

    // then
    const links = screen.getAllByRole("link");
    const accented = links[0]!;
    const fallback = links[1]!;
    expect(accented.style.getPropertyValue("--k")).toBe("#1aa6a0");
    expect(fallback.style.getPropertyValue("--k")).not.toBe("");
    expect(fallback.style.getPropertyValue("--k")).not.toBe("#1aa6a0");
  });

  it("tints the kind badge with the community accent colour", () => {
    // given
    mockUseCommunities.mockReturnValue({
      isLoading: false,
      isError: false,
      data: {
        content: [{ ...community, accentColor: "#1aa6a0" }],
        totalElements: 1,
        totalPages: 1,
        number: 0,
        size: 20,
      },
    } as never);

    // when
    renderList();

    // then
    expect(screen.getByText("Guild").style.getPropertyValue("--k")).toBe(
      "#1aa6a0",
    );
  });

  it("shows the community badge when it has one", () => {
    // given
    mockUseCommunities.mockReturnValue({
      isLoading: false,
      isError: false,
      data: {
        content: [{ ...community, badgeUrl: "https://cdn/pg.png" }],
        totalElements: 1,
        totalPages: 1,
        number: 0,
        size: 20,
      },
    } as never);

    // when
    renderList();

    // then
    expect(screen.getByRole("img", { name: /backend guild/i })).toHaveAttribute(
      "src",
      "https://cdn/pg.png",
    );
  });

  it("updates the filter input as the user types", async () => {
    // given
    mockUseCommunities.mockReturnValue({
      isLoading: false,
      isError: false,
      data: {
        content: [community],
        totalElements: 1,
        totalPages: 1,
        number: 0,
        size: 20,
      },
    } as never);
    renderList();

    // when
    await user.type(screen.getByLabelText(/filter communities/i), "guild");

    // then
    expect(screen.getByLabelText(/filter communities/i)).toHaveValue("guild");
  });
});
