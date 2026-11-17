import { render, screen, within } from "@testing-library/react";
import { MemoryRouter, Route, Routes } from "react-router-dom";
import { beforeEach, describe, expect, it, vi } from "vitest";

import { useIsDelegated } from "@/auth/delegated-session";
import { useMyWords } from "@/features/mentoring/api/use-mentoring";
import { wordsOf } from "@/features/mentoring/test-data";
import { WordsFromMentees } from "@/features/mentoring/ui/WordsFromMentees";

vi.mock("@/features/mentoring/api/use-mentoring", () => ({
  useMyWords: vi.fn(),
  useOpenWords: vi.fn(() => ({ mutate: vi.fn() })),
}));
vi.mock("@/auth/delegated-session", () => ({ useIsDelegated: vi.fn() }));

const words = wordsOf({ openedAt: "2026-11-21T10:00:00Z" });

function feed(list: (typeof words)[] | undefined) {
  vi.mocked(useMyWords).mockReturnValue({ data: list } as never);
}

function view(path = "/me/impact") {
  return render(
    <MemoryRouter initialEntries={[path]}>
      <Routes>
        <Route path="/me/impact" element={<WordsFromMentees />} />
        <Route
          path="/communities/:slug/me/impact"
          element={<WordsFromMentees />}
        />
      </Routes>
    </MemoryRouter>,
  );
}

beforeEach(() => {
  vi.clearAllMocks();
  vi.mocked(useIsDelegated).mockReturnValue(false);
});

describe("WordsFromMentees", () => {
  it("shows each mentee's words under the guild, the name and the dates", () => {
    // given
    feed([
      { ...words, endedAt: "2026-12-01T10:00:00Z" },
      {
        ...words,
        pairingId: 12,
        communitySlug: "backend",
        communityName: "Backend",
        menteeName: "Mia",
        body: "Great guidance.",
      },
    ]);

    // when
    view();

    // then
    const guild = screen.getByRole("heading", { name: "Platform" })
      .parentElement as HTMLElement;
    expect(within(guild).getByText("Noah")).toBeInTheDocument();
    expect(
      within(guild).getByText(/16 Nov 2026 to 1 Dec 2026$/),
    ).toBeInTheDocument();
    expect(within(guild).getByText("Thanks for the time.")).toBeInTheDocument();
    expect(
      screen.getByRole("heading", { name: "Backend" }),
    ).toBeInTheDocument();
    expect(screen.getByText(/since 16 Nov 2026$/)).toBeInTheDocument();
  });

  it("still shows no count or summary", () => {
    // given
    feed([words, { ...words, pairingId: 12, menteeName: "Mia" }]);

    // when
    const { container } = view();

    // then
    const digitOnly = [...container.querySelectorAll("*")]
      .flatMap((element) => [...element.childNodes])
      .filter(
        (node) =>
          node.nodeType === Node.TEXT_NODE &&
          /^\s*\d+\s*$/.test(node.textContent ?? ""),
      );
    expect(digitOnly).toEqual([]);
  });

  it("brings words not yet opened as a sealed envelope", () => {
    // given
    feed([wordsOf()]);

    // when
    view();

    // then
    expect(
      screen.getByRole("button", { name: "Open the words from Noah" }),
    ).toBeInTheDocument();
    expect(screen.queryByText("Thanks for the time.")).not.toBeInTheDocument();
  });

  it("renders nothing without words", () => {
    // given
    feed([]);

    // when
    const { container } = view();

    // then
    expect(container).toBeEmptyDOMElement();
  });

  it("renders nothing while the words load", () => {
    // given
    feed(undefined);

    // when
    const { container } = view();

    // then
    expect(container).toBeEmptyDOMElement();
  });

  it("inside a community shows only that community’s words, told apart by slug not name", () => {
    // given
    feed([
      words,
      {
        ...words,
        pairingId: 12,
        communitySlug: "platform-2",
        body: "Elsewhere.",
      },
    ]);

    // when
    view("/communities/platform/me/impact");

    // then
    expect(screen.getByText("Thanks for the time.")).toBeInTheDocument();
    expect(screen.queryByText("Elsewhere.")).not.toBeInTheDocument();
  });

  it("renders nothing in a delegated session (the hook itself never asks)", () => {
    // given
    vi.mocked(useIsDelegated).mockReturnValue(true);
    feed([words]);

    // when
    const { container } = view();

    // then
    expect(container).toBeEmptyDOMElement();
  });
});
