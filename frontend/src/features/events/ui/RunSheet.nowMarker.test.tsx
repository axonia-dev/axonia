import { act, render, screen } from "@testing-library/react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

import type { Session } from "@/features/events/domain/session";
import { RunSheet } from "@/features/events/ui/RunSheet";
import { aScheduledSession, aSection, aSlot } from "@/test/session-fixture";

vi.mock("@/features/events/ui/SectionBlock", () => ({
  SectionBlock: ({ section }: { section: { id: number } }) => (
    <div>section {section.id}</div>
  ),
}));

/** Three blocks of 10, 15 and 10 minutes, meeting at 20:00 on the series' own clock. */
function threeBlocks(overrides: Partial<Session> = {}): Session {
  return aScheduledSession({
    scheduledAt: "2026-08-15T18:00:00Z",
    seriesTimeZone: "Europe/Paris",
    durationMinutes: 35,
    sections: [
      aSection({ id: 1, slots: [aSlot({ id: 1, durationMinutes: 10 })] }),
      aSection({ id: 2, slots: [aSlot({ id: 2, durationMinutes: 15 })] }),
      aSection({ id: 3, slots: [aSlot({ id: 3, durationMinutes: 10 })] }),
    ],
    ...overrides,
  });
}

function renderAt(iso: string, session = threeBlocks()) {
  vi.setSystemTime(new Date(iso));
  return render(<RunSheet slug="dojo" session={session} mode="contribute" />);
}

/** How far down its row the mark claims to be, before the clamp that keeps it clear of the
 *  block start times sharing the gutter. */
function markerAt(): number {
  return Number.parseFloat(
    screen.getByTestId("now-marker").style.getPropertyValue("--at"),
  );
}

function bands(container: HTMLElement): string[] {
  return [...container.querySelectorAll("[data-part='band']")].map(
    (band) => (band as HTMLElement).style.background,
  );
}

describe("RunSheet, while the session is running", () => {
  beforeEach(() => {
    vi.useFakeTimers();
  });

  afterEach(() => {
    vi.useRealTimers();
  });

  it("marks the hour it is now on the session's own clock", () => {
    renderAt("2026-08-15T18:12:00Z");
    expect(screen.getByTestId("now-marker")).toHaveTextContent("20:12");
  });

  it("places the mark inside the block the session is on", () => {
    renderAt("2026-08-15T18:12:00Z");
    // Two minutes into the second block, which runs fifteen.
    expect(markerAt()).toBeCloseTo(13.3, 1);
  });

  it("moves the mark on as the minutes pass, without being asked again", () => {
    renderAt("2026-08-15T18:12:00Z");
    const before = markerAt();

    act(() => {
      vi.advanceTimersByTime(60_000);
    });

    expect(screen.getByTestId("now-marker")).toHaveTextContent("20:13");
    expect(markerAt()).toBeGreaterThan(before);
  });

  it("spends the rail behind the mark and leaves the agenda ahead of it in full", () => {
    const { container } = renderAt("2026-08-15T18:12:00Z");
    const [spent, running, ahead] = bands(container);
    expect(spent).toContain("color-mix");
    expect(spent).not.toContain("gradient");
    expect(running).toContain("gradient");
    expect(ahead).not.toContain("color-mix");
  });

  it("marks nothing before the session has started", () => {
    renderAt("2026-08-15T17:50:00Z");
    expect(screen.queryByTestId("now-marker")).not.toBeInTheDocument();
  });

  it("marks nothing once the agenda has run out", () => {
    renderAt("2026-08-15T18:40:00Z");
    expect(screen.queryByTestId("now-marker")).not.toBeInTheDocument();
  });

  it("marks the session as soon as its start time arrives", () => {
    renderAt("2026-08-15T17:59:30Z");

    act(() => {
      vi.advanceTimersByTime(30_000);
    });

    expect(screen.getByTestId("now-marker")).toHaveTextContent("20:00");
  });
});
