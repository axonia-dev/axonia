import { act, screen, waitFor } from "@testing-library/react";
// act is used synchronously to flush the debounce timer into React state
import userEvent from "@testing-library/user-event";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

import { renderView } from "@/features/expertise/ui/expertise-search-view-fixture";

vi.mock("@/features/expertise/api/use-expertise-search", () => ({
  useExpertiseSearch: vi.fn(),
}));

vi.mock("@/features/expertise/ui/CommunityKnows", () => ({
  CommunityKnows: ({
    onSelect,
    activeTag,
  }: {
    onSelect: (tag: string) => void;
    activeTag?: string;
  }) => (
    <button
      type="button"
      data-testid="community-knows"
      data-active={activeTag ?? ""}
      onClick={() => onSelect("kafka")}
    >
      pick kafka
    </button>
  ),
}));

vi.mock("@/features/expertise/ui/PractitionerList", () => ({
  PractitionerList: ({
    practitioners,
    isLoading,
  }: {
    practitioners: { userId: string }[];
    isLoading: boolean;
  }) => (
    <div data-testid="practitioner-list">
      {isLoading ? (
        <span>Loading…</span>
      ) : (
        <span>{practitioners.length} results</span>
      )}
    </div>
  ),
}));

vi.mock("@/features/learning/ui/LearnersBridge", () => ({
  LearnersBridge: ({ slug, term }: { slug: string; term: string }) => (
    <p>
      bridge to learners of {term} in {slug}
    </p>
  ),
}));

import { useExpertiseSearch } from "@/features/expertise/api/use-expertise-search";

const mockSearch = vi.mocked(useExpertiseSearch);

let user: ReturnType<typeof userEvent.setup>;

beforeEach(() => {
  user = userEvent.setup({ delay: null });
  vi.useFakeTimers({ shouldAdvanceTime: true });
  vi.clearAllMocks();
  mockSearch.mockReturnValue({
    data: [],
    isLoading: false,
    isError: false,
  } as never);
});

afterEach(() => {
  vi.useRealTimers();
});

describe("ExpertiseSearchView", () => {
  it("shows the community-knows browse view when term is blank", () => {
    // when
    renderView();

    // then
    expect(screen.getByTestId("community-knows")).toBeInTheDocument();
    expect(screen.queryByTestId("practitioner-list")).not.toBeInTheDocument();
  });

  it("searches the picked topic while keeping the constellation visible", async () => {
    // given
    renderView();

    // when
    await user.click(screen.getByTestId("community-knows"));

    // then - results appear AND the topic map persists, with the active topic
    await waitFor(() =>
      expect(screen.getByTestId("practitioner-list")).toBeInTheDocument(),
    );
    const constellation = screen.getByTestId("community-knows");
    expect(constellation).toBeInTheDocument();
    expect(constellation).toHaveAttribute("data-active", "kafka");
    expect(mockSearch).toHaveBeenLastCalledWith(
      "eng",
      expect.objectContaining({ term: "kafka" }),
    );
  });

  it("clears the search and returns to the idle map", async () => {
    // given a search is active
    renderView();
    await user.click(screen.getByTestId("community-knows"));
    await waitFor(() =>
      expect(screen.getByTestId("practitioner-list")).toBeInTheDocument(),
    );

    // when
    await user.click(screen.getByRole("button", { name: "clear" }));

    // then
    await waitFor(() =>
      expect(screen.queryByTestId("practitioner-list")).not.toBeInTheDocument(),
    );
    expect(screen.getByTestId("community-knows")).toHaveAttribute(
      "data-active",
      "",
    );
  });

  it("searches the term it mounts with, from a deep link", () => {
    // when
    renderView("eng", "?term=kafka");

    // then - the search runs immediately, with no typing needed
    expect(screen.getByTestId("practitioner-list")).toBeInTheDocument();
    expect(mockSearch).toHaveBeenCalledWith(
      "eng",
      expect.objectContaining({ term: "kafka" }),
    );
  });

  it("bridges a searched term to who wants to learn it", () => {
    // when
    renderView("eng", "?term=kafka");

    // then
    expect(
      screen.getByText("bridge to learners of kafka in eng"),
    ).toBeInTheDocument();
  });

  it("bridges nothing until a term is searched", () => {
    // when
    renderView();

    // then
    expect(screen.queryByText(/bridge to learners/)).not.toBeInTheDocument();
  });

  it("shows the practitioner list after typing a term", async () => {
    // when
    renderView();
    await user.type(
      screen.getByRole("searchbox", { name: /who knows/i }),
      "hexagon",
    );
    act(() => {
      vi.advanceTimersByTime(300);
    });

    // then
    await waitFor(() =>
      expect(screen.getByTestId("practitioner-list")).toBeInTheDocument(),
    );
    expect(mockSearch).toHaveBeenCalledWith(
      "eng",
      expect.objectContaining({ term: "hexagon" }),
    );
  });

  it("asks the search panel one question, with no include switch", () => {
    // when
    renderView();

    // then
    expect(screen.queryByRole("switch")).not.toBeInTheDocument();
    expect(
      screen.queryByRole("radio", { name: "Practised" }),
    ).not.toBeInTheDocument();
  });

  it("asks for everyone until the result filter is turned on", () => {
    // when
    renderView("eng", "?term=kafka");

    // then
    expect(screen.getByRole("radio", { name: "Anyone" })).toHaveAttribute(
      "aria-checked",
      "true",
    );
    expect(mockSearch).toHaveBeenCalledWith(
      "eng",
      expect.objectContaining({ demonstratedOnly: false }),
    );
  });

  it("narrows the result to people who practised here", async () => {
    // given
    renderView("eng", "?term=kafka");

    // when
    await user.click(screen.getByRole("radio", { name: "Practised" }));

    // then
    expect(mockSearch).toHaveBeenLastCalledWith(
      "eng",
      expect.objectContaining({ demonstratedOnly: true }),
    );
  });

  it("asks a picked area fresh, so its list matches the count on the tag", async () => {
    // given a filtered result is on screen
    renderView("eng", "?term=redis&who=demonstrated");

    // when
    await user.click(screen.getByTestId("community-knows"));

    // then
    expect(mockSearch).toHaveBeenLastCalledWith(
      "eng",
      expect.objectContaining({ term: "kafka", demonstratedOnly: false }),
    );
    expect(screen.getByRole("radio", { name: "Anyone" })).toHaveAttribute(
      "aria-checked",
      "true",
    );
  });

  it("keeps the filter while a non-empty term is edited", async () => {
    // given
    renderView("eng", "?term=kafka&who=demonstrated");

    // when
    await user.type(
      screen.getByRole("searchbox", { name: /who knows/i }),
      "-streams",
    );
    act(() => {
      vi.advanceTimersByTime(300);
    });

    // then
    await waitFor(() =>
      expect(mockSearch).toHaveBeenLastCalledWith(
        "eng",
        expect.objectContaining({
          term: "kafka-streams",
          demonstratedOnly: true,
        }),
      ),
    );
  });
});
