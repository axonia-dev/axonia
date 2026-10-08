import { act, screen, waitFor } from "@testing-library/react";
// act is used synchronously to flush the debounce timer into React state
import userEvent from "@testing-library/user-event";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

import { renderView } from "@/features/expertise/ui/expertise-search-view-fixture";

vi.mock("@/features/expertise/api/use-expertise-search", () => ({
  useExpertiseSearch: vi.fn(),
}));

vi.mock("@/features/expertise/ui/CommunityKnows", () => ({
  CommunityKnows: () => <div data-testid="community-knows" />,
}));

vi.mock("@/features/expertise/ui/PractitionerList", () => ({
  PractitionerList: () => <div data-testid="practitioner-list" />,
}));

vi.mock("@/features/learning/ui/LearnersBridge", () => ({
  LearnersBridge: () => null,
}));

import { useExpertiseSearch } from "@/features/expertise/api/use-expertise-search";

const mockSearch = vi.mocked(useExpertiseSearch);

let user: ReturnType<typeof userEvent.setup>;

function location() {
  return screen.getByTestId("location");
}

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

describe("ExpertiseSearchView URL", () => {
  it("keeps the searched term in the URL, replacing the entry rather than adding one", async () => {
    // given
    renderView("eng", "?view=search");

    // when
    await user.type(
      screen.getByRole("searchbox", { name: /who knows/i }),
      "hexagon",
    );
    act(() => {
      vi.advanceTimersByTime(300);
    });

    // then
    await waitFor(() =>
      expect(location()).toHaveTextContent("?view=search&term=hexagon"),
    );
    expect(location()).toHaveAttribute("data-navigation", "REPLACE");
  });

  it("drops the term from the URL once the search is cleared", async () => {
    // given
    renderView("eng", "?term=kafka");

    // when
    await user.click(screen.getByRole("button", { name: "clear" }));

    // then
    await waitFor(() => expect(location()).toBeEmptyDOMElement());
  });

  it("lands on the filter a deep link carries", () => {
    // when
    renderView("eng", "?term=kafka&who=demonstrated");

    // then
    expect(screen.getByRole("radio", { name: "Practised" })).toHaveAttribute(
      "aria-checked",
      "true",
    );
    expect(mockSearch).toHaveBeenCalledWith(
      "eng",
      expect.objectContaining({ demonstratedOnly: true }),
    );
  });

  it("writes the chosen filter beside the term", async () => {
    // given
    renderView("eng", "?term=kafka");

    // when
    await user.click(screen.getByRole("radio", { name: "Practised" }));

    // then
    await waitFor(() =>
      expect(location().textContent).toBe("?term=kafka&who=demonstrated"),
    );
    expect(location()).toHaveAttribute("data-navigation", "REPLACE");
  });

  it("takes the filter with it when the search is cleared", async () => {
    // given
    renderView("eng", "?term=kafka&who=demonstrated");

    // when
    await user.click(screen.getByRole("button", { name: "clear" }));

    // then - no term, no control on screen, nothing left in the URL
    await waitFor(() => expect(location()).toBeEmptyDOMElement());
  });

  it("takes the filter with it when the box is emptied by hand", async () => {
    // given
    renderView("eng", "?term=kafka&who=demonstrated");

    // when
    await user.clear(screen.getByRole("searchbox", { name: /who knows/i }));
    act(() => {
      vi.advanceTimersByTime(300);
    });

    // then
    await waitFor(() => expect(location()).toBeEmptyDOMElement());
  });

  it("drops a filter a deep link carries with no term to filter", async () => {
    // when
    renderView("eng", "?who=demonstrated");

    // then
    await waitFor(() => expect(location()).toBeEmptyDOMElement());
    expect(mockSearch).toHaveBeenCalledWith(
      "eng",
      expect.objectContaining({ demonstratedOnly: false }),
    );
  });

  it("drops the parameter when the default comes back", async () => {
    // given
    renderView("eng", "?term=kafka&who=demonstrated");

    // when
    await user.click(screen.getByRole("radio", { name: "Anyone" }));

    // then
    await waitFor(() => expect(location().textContent).toBe("?term=kafka"));
  });
});
