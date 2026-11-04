import { act, render, screen } from "@testing-library/react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

import { EMPTY_PLACE } from "@/features/events/domain/meeting-place";
import type { ScheduleEntry } from "@/features/events/domain/schedule";
import { NextCountdown } from "@/features/events/ui/timeline/NextCountdown";

const START = "2026-01-09T18:00:00Z";

function entry(overrides: Partial<ScheduleEntry> = {}): ScheduleEntry {
  return {
    sessionId: 1,
    seriesId: 1,
    title: null,
    displayTitle: "Session",
    occurrenceDate: "2026-01-09",
    scheduledAt: START,
    hasTime: true,
    seriesTimeZone: "Europe/Paris",
    durationMinutes: 60,
    status: "SCHEDULED",
    virtual: false,
    place: EMPTY_PLACE,
    sections: [],
    ...overrides,
  };
}

function at(msBefore: number) {
  vi.setSystemTime(new Date(new Date(START).getTime() - msBefore));
}

describe("NextCountdown", () => {
  beforeEach(() => {
    vi.useFakeTimers();
  });
  afterEach(() => {
    vi.useRealTimers();
  });

  it("shows four tiles and ticks the seconds", () => {
    // given
    at(((2 * 24 + 7) * 60 + 11) * 60_000 + 58_000);
    const { container } = render(<NextCountdown entry={entry()} />);
    // then
    expect(container.textContent).toBe("2d07h11m58s");
    // when
    act(() => {
      vi.advanceTimersByTime(1000);
    });
    // then
    expect(container.textContent).toBe("2d07h11m57s");
  });

  it("counts mm:ss in the last hour", () => {
    // given
    at(42 * 60_000 + 10_000);
    // when
    render(<NextCountdown entry={entry()} />);
    // then
    expect(screen.getByText("Starts in 42:10")).toBeInTheDocument();
  });

  it("says running once started", () => {
    // given
    at(-60_000);
    // when
    render(<NextCountdown entry={entry()} />);
    // then
    expect(screen.getByText("Running now")).toBeInTheDocument();
  });

  it("renders nothing for an untimed entry", () => {
    // given
    at(3_600_000 * 5);
    // when
    const { container } = render(
      <NextCountdown entry={entry({ hasTime: false })} />,
    );
    // then
    expect(container).toBeEmptyDOMElement();
  });

  it("hides the tiles from assistive tech and announces nothing live", () => {
    // given
    at(3_600_000 * 5);
    // when
    const { container } = render(<NextCountdown entry={entry()} />);
    // then
    expect(container.firstElementChild).toHaveAttribute("aria-hidden", "true");
    expect(container.querySelector("[aria-live]")).toBeNull();
  });

  it("schedules no per-second tick while off screen", () => {
    // given an observer that reports the countdown out of view
    const real = window.IntersectionObserver;
    window.IntersectionObserver = class {
      constructor(private readonly report: IntersectionObserverCallback) {}
      observe(target: Element) {
        this.report(
          [{ target, isIntersecting: false } as IntersectionObserverEntry],
          this as unknown as IntersectionObserver,
        );
      }
      disconnect() {}
    } as unknown as typeof IntersectionObserver;
    at(3_600_000 * 5);
    // when
    const { container } = render(<NextCountdown entry={entry()} />);
    const shown = container.textContent;
    // then: the one timer left is the page's wake for the last hour
    expect(vi.getTimerCount()).toBe(1);
    act(() => {
      vi.advanceTimersByTime(3000);
    });
    expect(container.textContent).toBe(shown);
    window.IntersectionObserver = real;
  });

  it("schedules no per-second tick while the tab is hidden", () => {
    // given
    const visibility = vi
      .spyOn(document, "visibilityState", "get")
      .mockReturnValue("hidden");
    at(3_600_000 * 5);
    // when
    render(<NextCountdown entry={entry()} />);
    // then
    expect(vi.getTimerCount()).toBe(1);
    visibility.mockRestore();
  });

  it("ticks once each second while seen", () => {
    // given
    at(3_600_000 * 5);
    // when
    render(<NextCountdown entry={entry()} />);
    // then: the page's phase wake and the countdown's own second
    expect(vi.getTimerCount()).toBe(2);
  });
});
