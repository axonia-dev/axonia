import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

import { useMe } from "@/features/identity/api/use-me";
import type { Journal } from "@/features/mentoring/domain/mentoring";
import { journalOf, noteOf, topicOf } from "@/features/mentoring/test-data";
import { JournalTrail } from "@/features/mentoring/ui/trail/JournalTrail";
import { prefersReducedMotion, restoreMotion } from "@/test/motion";

vi.mock("@/features/identity/api/use-me", () => ({ useMe: vi.fn() }));

const done = topicOf({ id: 1, label: "Testing strategy", state: "DONE" });
const doing = topicOf({ id: 2, label: "Code review", state: "IN_PROGRESS" });
const todo = topicOf({ id: 3, label: "Writing an RFC", state: "TODO" });

const journal: Journal = journalOf({
  topics: [todo, doing, done],
  notes: [
    noteOf({
      id: 1,
      body: "Start with characterisation tests.",
      topicIds: [1],
    }),
    noteOf({
      id: 2,
      mine: false,
      body: "Fourteen tests written.",
      topicIds: [1],
    }),
    noteOf({ id: 3, body: "Ask, do not tell.", topicIds: [2] }),
  ],
});

/** Answers a phone-width query as asked and every other query with false. */
function onAPhone(phone: boolean) {
  window.matchMedia = vi.fn((query: string) => ({
    matches: phone && query.includes("max-width"),
    media: query,
    onchange: null,
    addEventListener: vi.fn(),
    removeEventListener: vi.fn(),
    addListener: vi.fn(),
    removeListener: vi.fn(),
    dispatchEvent: vi.fn(),
  }));
}

beforeEach(() => {
  vi.clearAllMocks();
  prefersReducedMotion(false);
  vi.mocked(useMe).mockReturnValue({ data: undefined } as never);
});

afterEach(restoreMotion);

describe("JournalTrail", () => {
  it("opens a topic from its stop", async () => {
    // given
    const onOpenTopic = vi.fn();
    render(<JournalTrail journal={journal} onOpenTopic={onOpenTopic} />);

    // when
    await userEvent.click(screen.getByRole("button", { name: /^Code review/ }));

    // then
    expect(onOpenTopic).toHaveBeenCalledWith(2);
  });

  it("shows a stop's notes on focus", async () => {
    // given
    render(<JournalTrail journal={journal} onOpenTopic={vi.fn()} />);

    // when
    await userEvent.tab();

    // then
    const stop = screen.getByRole("button", { name: /^Testing strategy/ });
    expect(stop).toHaveFocus();
    expect(stop).toHaveAccessibleDescription(
      /Start with characterisation tests/,
    );
    expect(screen.getByRole("tooltip")).toHaveTextContent(
      "Fourteen tests written.",
    );
  });

  it("walks the stops in trail order", () => {
    // when
    render(<JournalTrail journal={journal} onOpenTopic={vi.fn()} />);

    // then
    expect(
      screen.getAllByRole("button").map((stop) => stop.textContent),
    ).toEqual([
      "Testing strategyDone · 2 notes",
      "Code reviewIn progress · 1 note",
      "Writing an RFCTo do",
    ]);
  });

  it("draws footprints without any count", () => {
    // when
    render(<JournalTrail journal={journal} onOpenTopic={vi.fn()} />);

    // then
    const ground = screen.getByTestId("trail-ground");
    expect(ground).toHaveAttribute("aria-hidden", "true");
    expect(
      screen.getByTestId("footprints").querySelectorAll("ellipse"),
    ).toHaveLength(3);
    expect(ground.querySelector("text")).toBeNull();
  });

  it("turns vertical on a phone", () => {
    // given
    onAPhone(true);

    // when
    const { container } = render(
      <JournalTrail journal={journal} onOpenTopic={vi.fn()} />,
    );

    // then
    expect(container.querySelector("[data-orientation]")).toHaveAttribute(
      "data-orientation",
      "vertical",
    );
  });

  it("does not pulse under reduced motion", () => {
    // given
    prefersReducedMotion(true);

    // when
    const { container } = render(
      <JournalTrail journal={journal} onOpenTopic={vi.fn()} />,
    );

    // then
    expect(container.innerHTML).not.toContain("stop-pulse");
    expect(container.innerHTML).not.toContain("walker-bob");
  });

  it("pulses the stop in progress when motion is welcome", () => {
    // when
    const { container } = render(
      <JournalTrail journal={journal} onOpenTopic={vi.fn()} />,
    );

    // then
    expect(container.innerHTML).toContain("stop-pulse");
  });

  it("invites topics when the trail has no stop", () => {
    // when
    render(
      <JournalTrail
        journal={journalOf({ topics: [] })}
        onOpenTopic={vi.fn()}
      />,
    );

    // then
    expect(screen.getByText(/No stops yet/)).toBeInTheDocument();
  });

  it("describes each stop by its notes from the moment focus arrives", () => {
    // when
    render(<JournalTrail journal={journal} onOpenTopic={vi.fn()} />);

    // then
    expect(
      screen.getByRole("button", { name: /^Testing strategy/ }),
    ).toHaveAccessibleDescription(/Start with characterisation tests/);
    expect(screen.queryByRole("tooltip")).not.toBeInTheDocument();
  });

  it("hides a stop's card on Escape", async () => {
    // given
    render(<JournalTrail journal={journal} onOpenTopic={vi.fn()} />);
    await userEvent.tab();
    expect(screen.getByRole("tooltip")).toBeInTheDocument();

    // when
    await userEvent.keyboard("{Escape}");

    // then
    expect(screen.queryByRole("tooltip")).not.toBeInTheDocument();
    expect(
      screen.getByRole("button", { name: /^Testing strategy/ }),
    ).toHaveFocus();
  });
});
