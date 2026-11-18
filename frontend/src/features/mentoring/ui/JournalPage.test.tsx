import { screen } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";

import { useIsDelegated } from "@/auth/delegated-session";
import { journal } from "@/features/mentoring/test-data";
import { hooks, show } from "@/test/journal-page-view";

vi.mock("@/auth/delegated-session", () => ({ useIsDelegated: vi.fn() }));
vi.mock("@/features/identity/api/use-me", () => ({ useMe: vi.fn() }));
vi.mock("@/features/mentoring/api/use-mentoring", () => ({
  useJournal: vi.fn(),
  useEndPairing: vi.fn(),
  useJournalWrite: vi.fn(),
  useMeetingPlan: vi.fn(),
  useWords: vi.fn(),
  useShowing: vi.fn(),
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

  it("adds that the community sees a shown pairing exists", () => {
    // given
    hooks();

    // when
    show({ showing: { visibility: "SHOWN", iSaidYes: true } });

    // then
    expect(
      screen.getByText(
        "Only the two people in this pairing see this journal. Members of the community see only that the pairing exists. It is part of the organization’s data export.",
      ),
    ).toBeInTheDocument();
  });

  it("asks above the trail whether to show a pairing begun in private", () => {
    // given
    hooks();

    // when
    show({ showing: { visibility: "ASKING", iSaidYes: false } });

    // then
    const ask = screen.getByRole("heading", {
      name: "Show this pairing on the Mentoring page?",
    });
    const trail = screen.getByRole("region", { name: "Trail of the pairing" });
    expect(
      ask.compareDocumentPosition(trail) & Node.DOCUMENT_POSITION_FOLLOWING,
    ).toBeTruthy();
    expect(
      screen.getByText(
        "Only the two people in this pairing see this journal. It is part of the organization’s data export.",
      ),
    ).toBeInTheDocument();
  });

  it("adds nothing about the community once a shown pairing has ended", () => {
    // given
    hooks();

    // when
    show({
      pairing: { ...journal.pairing, endedAt: "2026-11-17T10:00:00Z" },
      showing: { visibility: "SHOWN", iSaidYes: true },
    });

    // then
    expect(
      screen.getByText(
        "Only the two people in this pairing see this journal. It is part of the organization’s data export.",
      ),
    ).toBeInTheDocument();
    expect(screen.queryByText(/Members of the community/)).toBeNull();
  });
});
