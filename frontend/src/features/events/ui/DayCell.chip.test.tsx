import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter } from "react-router-dom";
import { beforeEach, describe, expect, it, vi } from "vitest";

import { EMPTY_PLACE } from "@/features/events/domain/meeting-place";
import type { ScheduleEntry } from "@/features/events/domain/schedule";
import { Chip } from "@/features/events/ui/ScheduleChip";

let user: ReturnType<typeof userEvent.setup>;

beforeEach(() => {
  user = userEvent.setup({ delay: null });
});

function entry(overrides: Partial<ScheduleEntry> = {}): ScheduleEntry {
  return {
    sessionId: 7,
    seriesId: 1,
    title: "Guild Sync",
    occurrenceDate: "2026-08-20",
    scheduledAt: "2026-08-20T12:00:00Z",
    hasTime: true,
    seriesTimeZone: "Europe/Zurich",
    durationMinutes: 60,
    status: "SCHEDULED",
    virtual: false,
    place: EMPTY_PLACE,
    sections: [],
    ...overrides,
  };
}

function renderChip(e: ScheduleEntry) {
  render(
    <MemoryRouter>
      <Chip entry={e} seriesList={[]} />
    </MemoryRouter>,
  );
}

describe("Chip", () => {
  it("states the hour the session starts at", () => {
    renderChip(entry());
    expect(screen.getByTestId("chip-time")).toHaveTextContent(/^\d{2}:\d{2}$/);
    expect(screen.getByText("Guild Sync")).toBeInTheDocument();
  });

  it("states no hour for an occurrence nobody set one for", () => {
    renderChip(entry({ hasTime: false, scheduledAt: "2026-08-19T22:00:00Z" }));
    expect(screen.queryByTestId("chip-time")).not.toBeInTheDocument();
    expect(screen.getByText("Guild Sync")).toBeInTheDocument();
  });
});

describe("Chip gestures", () => {
  it("selects on a single click rather than navigating", async () => {
    const onSelect = vi.fn();
    const onOpen = vi.fn();
    render(
      <MemoryRouter>
        <Chip
          entry={entry()}
          seriesList={[]}
          onSelect={onSelect}
          onOpen={onOpen}
        />
      </MemoryRouter>,
    );
    await user.click(screen.getByRole("button", { name: /guild sync/i }));
    expect(onSelect).toHaveBeenCalledTimes(1);
    expect(onOpen).not.toHaveBeenCalled();
  });

  it("opens the session on a double click", async () => {
    const onOpen = vi.fn();
    render(
      <MemoryRouter>
        <Chip
          entry={entry()}
          seriesList={[]}
          onSelect={vi.fn()}
          onOpen={onOpen}
        />
      </MemoryRouter>,
    );
    await user.dblClick(screen.getByRole("button", { name: /guild sync/i }));
    expect(onOpen).toHaveBeenCalledTimes(1);
  });

  it("marks the chip that is up next", () => {
    render(
      <MemoryRouter>
        <Chip entry={entry()} seriesList={[]} nextUp />
      </MemoryRouter>,
    );
    expect(screen.getByTestId("chip-next-up")).toBeInTheDocument();
  });

  it("opens on a single click when only onOpen is wired", async () => {
    const onOpen = vi.fn();
    render(
      <MemoryRouter>
        <Chip entry={entry()} seriesList={[]} onOpen={onOpen} />
      </MemoryRouter>,
    );
    await user.click(screen.getByRole("button", { name: /guild sync/i }));
    expect(onOpen).toHaveBeenCalledTimes(1);
  });

  it("opens on Enter when only onOpen is wired", async () => {
    const onOpen = vi.fn();
    render(
      <MemoryRouter>
        <Chip entry={entry()} seriesList={[]} onOpen={onOpen} />
      </MemoryRouter>,
    );
    screen.getByRole("button", { name: /guild sync/i }).focus();
    await user.keyboard("{Enter}");
    expect(onOpen).toHaveBeenCalledTimes(1);
  });

  /** The chip is a disclosure: it says which panel it opens and whether that panel is showing, so
   *  a reader who cannot see the panel appear is told it did. */
  it("names the panel it opens and whether that panel is showing", () => {
    function renderWith(selected: boolean) {
      return (
        <MemoryRouter>
          <Chip
            entry={entry()}
            seriesList={[]}
            panelId="calendar-panel"
            selected={selected}
            onSelect={vi.fn()}
          />
        </MemoryRouter>
      );
    }
    const { rerender } = render(renderWith(false));
    const chip = screen.getByRole("button", { name: /guild sync/i });
    expect(chip).toHaveAttribute("aria-controls", "calendar-panel");
    expect(chip).toHaveAttribute("aria-expanded", "false");

    rerender(renderWith(true));
    expect(screen.getByRole("button", { name: /guild sync/i })).toHaveAttribute(
      "aria-expanded",
      "true",
    );
  });

  it("shows a live dot instead of the hour while the session is running", () => {
    vi.useFakeTimers({ toFake: ["Date"] });
    vi.setSystemTime(new Date("2026-08-20T12:10:00Z"));
    renderChip(
      entry({ place: { ...EMPTY_PLACE, joinUrl: "https://meet.example/x" } }),
    );
    expect(screen.getByTestId("chip-live")).toBeInTheDocument();
    expect(screen.queryByTestId("chip-time")).not.toBeInTheDocument();
    vi.useRealTimers();
  });
});
