import { screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";

import type { Practitioner } from "@/features/expertise/domain/expertise";
import { renderView } from "@/features/expertise/ui/expertise-search-view-fixture";

vi.mock("@/features/expertise/api/use-expertise-search", () => ({
  useExpertiseSearch: vi.fn(),
}));

vi.mock("@/features/expertise/ui/CommunityKnows", () => ({
  CommunityKnows: () => null,
}));

vi.mock("@/features/expertise/ui/PractitionerList", () => ({
  PractitionerList: ({
    practitioners,
    who,
  }: {
    practitioners: { userId: string }[];
    who: string;
  }) => (
    <div data-testid="practitioner-list" data-who={who}>
      {practitioners.length} results
    </div>
  ),
}));

vi.mock("@/features/learning/ui/LearnersBridge", () => ({
  LearnersBridge: () => null,
}));

import { useExpertiseSearch } from "@/features/expertise/api/use-expertise-search";

const mockSearch = vi.mocked(useExpertiseSearch);

let user: ReturnType<typeof userEvent.setup>;

function person(
  userId: string,
  tier: Practitioner["tier"],
  openToQuestions = false,
): Practitioner {
  return {
    userId,
    displayName: userId,
    avatarUrl: null,
    orgUnit: null,
    tier,
    evidence: {
      presentedCount: 0,
      contributedCount: 0,
      offeredCount: 0,
      recent: [],
    },
    openToQuestions,
    declaredAt: openToQuestions ? "2026-11-01T00:00:00Z" : null,
  };
}

function location() {
  return screen.getByTestId("location");
}

beforeEach(() => {
  user = userEvent.setup({ delay: null });
  vi.clearAllMocks();
  mockSearch.mockReturnValue({
    data: [
      person("ana", "PRACTITIONER", true),
      person("bo", "CONTRIBUTOR"),
      person("cy", "INTERESTED"),
      person("di", "OPEN_TO_QUESTIONS", true),
      person("ed", "OPEN_TO_QUESTIONS", true),
    ],
    isLoading: false,
    isError: false,
  } as never);
});

describe("ExpertiseSearchView narrowing", () => {
  it("narrows the result to people who practised here, without asking again", async () => {
    // given
    renderView("eng", "?term=kafka");

    // when
    await user.click(screen.getByRole("radio", { name: /^Practised/ }));

    // then
    expect(screen.getByText("2 results")).toBeInTheDocument();
    expect(screen.getByTestId("practitioner-list")).toHaveAttribute(
      "data-who",
      "demonstrated",
    );
    expect(mockSearch).not.toHaveBeenCalledWith(
      "eng",
      expect.objectContaining({ demonstratedOnly: true }),
    );
  });

  it("openNarrowingWritesWhoOpen", async () => {
    // given
    renderView("eng", "?term=kafka");

    // when
    await user.click(screen.getByRole("radio", { name: /^Open to questions/ }));

    // then
    expect(screen.getByText("3 results")).toBeInTheDocument();
    await waitFor(() =>
      expect(location().textContent).toBe("?term=kafka&who=open"),
    );
    expect(location()).toHaveAttribute("data-navigation", "REPLACE");
  });

  it("whoOpenSurvivesReload", () => {
    // when
    renderView("eng", "?term=kafka&who=open");

    // then
    expect(
      screen.getByRole("radio", { name: /^Open to questions/ }),
    ).toHaveAttribute("aria-checked", "true");
    expect(screen.getByText("3 results")).toBeInTheDocument();
    expect(location().textContent).toBe("?term=kafka&who=open");
  });

  it("clearingTheTermDropsWho", async () => {
    // given
    renderView("eng", "?term=kafka&who=open");

    // when
    await user.click(screen.getByRole("button", { name: "clear" }));

    // then
    await waitFor(() => expect(location()).toBeEmptyDOMElement());
  });

  it("segmentsShowCounts", () => {
    // when
    renderView("eng", "?term=kafka");

    // then
    const names = screen
      .getAllByRole("radio")
      .map((radio) => radio.textContent);
    expect(names).toEqual(["Anyone5", "Practised2", "Open to questions3"]);
  });
});
