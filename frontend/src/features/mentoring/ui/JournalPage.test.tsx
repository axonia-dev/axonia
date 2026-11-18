import { screen } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";

import { useIsDelegated } from "@/auth/delegated-session";
import { hooks, show } from "@/test/journal-page-view";

vi.mock("@/auth/delegated-session", () => ({ useIsDelegated: vi.fn() }));
vi.mock("@/features/identity/api/use-me", () => ({ useMe: vi.fn() }));
vi.mock("@/features/mentoring/api/use-mentoring", () => ({
  useJournal: vi.fn(),
  useEndPairing: vi.fn(),
  useJournalWrite: vi.fn(),
  useMeetingPlan: vi.fn(),
  useWords: vi.fn(),
}));

beforeEach(() => {
  vi.clearAllMocks();
  vi.mocked(useIsDelegated).mockReturnValue(false);
});

describe("JournalPage", () => {
  it("the privacy line is shown above the journal", () => {
    // given
    hooks();

    // when
    show();

    // then
    const line = screen.getByText(
      "Only the two people in this pairing see this journal. It is part of the organization’s data export.",
    );
    const topics = screen.getByRole("heading", { name: "Topics" });
    expect(
      line.compareDocumentPosition(topics) & Node.DOCUMENT_POSITION_FOLLOWING,
    ).toBeTruthy();
  });

  it("names the partner, the role, the guild and the dates", () => {
    // given
    hooks();

    // when
    show();

    // then
    expect(screen.getByRole("heading", { name: "Ada" })).toBeInTheDocument();
    expect(
      screen.getByText(/Mentor · Platform · since 16 Nov 2026/),
    ).toBeInTheDocument();
  });

  it("lays the notes out as a logbook under the board", () => {
    // given
    hooks();

    // when
    show();

    // then
    const board = screen.getByRole("heading", { name: "Topics" });
    const logbook = screen.getByRole("heading", { name: "Logbook" });
    expect(
      board.compareDocumentPosition(logbook) & Node.DOCUMENT_POSITION_FOLLOWING,
    ).toBeTruthy();
    expect(screen.getByText("Met, covered unit tests.")).toBeInTheDocument();
  });

  it("shows the topics on a board of three columns", () => {
    // given
    hooks();

    // when
    show();

    // then
    expect(screen.getByRole("region", { name: "To do" })).toBeInTheDocument();
    expect(
      screen.getByRole("region", { name: "In progress" }),
    ).toHaveTextContent("Giving feedback");
    expect(screen.getByRole("region", { name: "Done" })).toBeInTheDocument();
  });
});
