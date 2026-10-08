import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter, useParams } from "react-router-dom";
import { beforeEach, describe, expect, it, vi } from "vitest";

import { useCommunity } from "@/features/community/api/use-communities";
import { useMe } from "@/features/identity/api/use-me";
import { useMyContributions } from "@/features/recognition/api/use-recognition";
import { MyImpactView } from "@/features/recognition/ui/MyImpactView";

vi.mock("@/features/recognition/api/use-recognition", () => ({
  useMyContributions: vi.fn(),
}));
vi.mock("@/features/identity/api/use-me", () => ({ useMe: vi.fn() }));
vi.mock("@/features/community/api/use-communities", () => ({
  useCommunity: vi.fn(),
}));
vi.mock("react-router-dom", async (importOriginal) => ({
  ...(await importOriginal<typeof import("react-router-dom")>()),
  useParams: vi.fn(() => ({})),
}));

const mockUseMyContributions = vi.mocked(useMyContributions);

function onPage(community?: { id: number; slug: string }) {
  vi.mocked(useParams).mockReturnValue(
    community === undefined ? {} : { slug: community.slug },
  );
  vi.mocked(useCommunity).mockReturnValue({
    data: community === undefined ? undefined : { id: community.id },
  } as never);
}

function renderView() {
  render(
    <MemoryRouter>
      <MyImpactView />
    </MemoryRouter>,
  );
}

function contribution(
  overrides: Partial<{
    contributionId: number;
    communityId: number;
    communityName: string | null;
    title: string;
    occurredAt: string;
    loveCount: number;
  }> = {},
) {
  return {
    contributionType: "SLOT_PRESENTED" as const,
    contributionId: 1,
    communityId: 1,
    communitySlug: "eng",
    communityName: "Engineering",
    title: "Resilience talk",
    occurredAt: "2026-06-01T00:00:00Z",
    loveCount: 0,
    sessionId: null,
    typeId: null,
    typeLabel: null,
    typeIcon: null,
    typeAccentColor: null,
    ...overrides,
  };
}

let user: ReturnType<typeof userEvent.setup>;

beforeEach(() => {
  user = userEvent.setup({ delay: null });
  vi.clearAllMocks();
  vi.mocked(useMe).mockReturnValue({
    data: { id: "u1", displayName: "Gabriel Dinant" },
  } as never);
  onPage();
});

describe("MyImpactView", () => {
  it("invites contributions when there are none", () => {
    mockUseMyContributions.mockReturnValue({
      data: [],
      isLoading: false,
      isError: false,
    } as never);
    renderView();
    expect(
      screen.getByText(/talks and captured artifacts appear here/i),
    ).toBeInTheDocument();
  });

  it("renders the header standing and a contribution card", () => {
    mockUseMyContributions.mockReturnValue({
      data: [
        {
          contributionType: "SLOT_PRESENTED",
          contributionId: 9,
          communityId: 1,
          communitySlug: "eng",
          title: "Resilience talk",
          occurredAt: "2026-06-01T00:00:00Z",
          loveCount: 2,
          sessionId: null,
          typeId: null,
          typeLabel: null,
          typeIcon: null,
          typeAccentColor: null,
        },
      ],
      isLoading: false,
      isError: false,
    } as never);
    renderView();
    expect(screen.getByText("My impact")).toBeInTheDocument();
    expect(
      screen.getByRole("link", { name: "Resilience talk" }),
    ).toBeInTheDocument();
    expect(
      screen.getByLabelText("Appreciated by 2 people"),
    ).toBeInTheDocument();
  });

  it("says the contributions could not load, claiming none in the header", async () => {
    // given
    const refetch = vi.fn();
    mockUseMyContributions.mockReturnValue({
      data: undefined,
      isLoading: false,
      isError: true,
      errorUpdateCount: 1,
      isFetching: false,
      refetch,
    } as never);
    renderView();

    // when
    await userEvent.click(screen.getByRole("button", { name: "Try again" }));

    // then
    expect(screen.getByRole("alert")).toHaveTextContent(
      "Could not load these contributions.",
    );
    expect(
      screen.queryByText(
        "Contributions appear here as people appreciate them.",
      ),
    ).not.toBeInTheDocument();
    expect(refetch).toHaveBeenCalledOnce();
  });

  it("shows the empty state when there are no contributions", () => {
    mockUseMyContributions.mockReturnValue({
      data: [],
      isLoading: false,
      isError: false,
    } as never);
    renderView();
    expect(screen.getByText(/no contributions yet/i)).toBeInTheDocument();
  });

  it("shows a filtered-empty state with a working clear action", async () => {
    mockUseMyContributions.mockReturnValue({
      data: [contribution({ title: "Only match" })],
      isLoading: false,
      isError: false,
    } as never);
    renderView();
    await user.type(screen.getByRole("searchbox"), "zzz");
    expect(
      screen.getByText(/no contributions match these filters/i),
    ).toBeInTheDocument();
    await user.click(screen.getByRole("button", { name: /clear filters/i }));
    expect(screen.getByText("Only match")).toBeInTheDocument();
  });

  it("shows Show more and renders only the first page of 25 items", () => {
    mockUseMyContributions.mockReturnValue({
      data: Array.from({ length: 26 }, (_, i) =>
        contribution({ contributionId: i + 1, title: `Item ${i + 1}` }),
      ),
      isLoading: false,
      isError: false,
    } as never);
    renderView();
    expect(screen.getAllByRole("listitem")).toHaveLength(25);
    expect(
      screen.getByRole("button", { name: /show more/i }),
    ).toBeInTheDocument();
  });

  describe("inside a community", () => {
    const ENGINEERING = { id: 1, slug: "engineering" };

    it("shows only that community's contributions", () => {
      onPage(ENGINEERING);
      mockUseMyContributions.mockReturnValue({
        data: [
          contribution({
            contributionId: 1,
            communityId: 1,
            communityName: "Engineering",
            title: "Engineering talk",
          }),
          contribution({
            contributionId: 2,
            communityId: 2,
            communityName: "Data",
            title: "Data talk",
          }),
        ],
        isLoading: false,
        isError: false,
      } as never);
      renderView();
      expect(screen.getByText("Engineering talk")).toBeInTheDocument();
      expect(screen.queryByText("Data talk")).not.toBeInTheDocument();
    });

    it("reflects the narrowed count in the header standing, naming no community", () => {
      onPage(ENGINEERING);
      mockUseMyContributions.mockReturnValue({
        data: [
          contribution({ contributionId: 1, communityId: 1 }),
          contribution({ contributionId: 2, communityId: 2 }),
        ],
        isLoading: false,
        isError: false,
      } as never);
      renderView();
      expect(
        screen.getByText("Contributed 1 talk, appreciated by peers."),
      ).toBeInTheDocument();
    });

    it("shows a community-scoped empty state when this community has nothing yet, even though another does", () => {
      onPage(ENGINEERING);
      mockUseMyContributions.mockReturnValue({
        data: [contribution({ contributionId: 1, communityId: 2 })],
        isLoading: false,
        isError: false,
      } as never);
      renderView();
      expect(
        screen.getByText(/no contributions in this community yet/i),
      ).toBeInTheDocument();
    });

    it("waits for the community rather than claiming it holds nothing", () => {
      vi.mocked(useParams).mockReturnValue({ slug: ENGINEERING.slug });
      vi.mocked(useCommunity).mockReturnValue({
        data: undefined,
        isLoading: true,
      } as never);
      mockUseMyContributions.mockReturnValue({
        data: [contribution({ contributionId: 1, communityId: 1 })],
        isLoading: false,
        isError: false,
      } as never);
      renderView();
      expect(screen.getByText("Loading…")).toBeInTheDocument();
      expect(screen.queryByText(/no contributions/i)).not.toBeInTheDocument();
    });
  });
});
