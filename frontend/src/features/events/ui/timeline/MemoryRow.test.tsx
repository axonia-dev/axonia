import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render, screen } from "@testing-library/react";
import { MemoryRouter } from "react-router-dom";
import { describe, expect, it } from "vitest";

import { EMPTY_PLACE } from "@/features/events/domain/meeting-place";
import { NO_TRACES } from "@/features/events/domain/memory";
import type { ScheduleEntry } from "@/features/events/domain/schedule";
import { MemoryRow } from "@/features/events/ui/timeline/MemoryRow";

const SCHEDULED_AT = "2026-01-02T18:00:00Z";

const ENTRY: ScheduleEntry = {
  sessionId: 7,
  seriesId: 1,
  title: null,
  occurrenceDate: "2026-01-02",
  scheduledAt: SCHEDULED_AT,
  hasTime: true,
  seriesTimeZone: "Europe/Paris",
  durationMinutes: 60,
  status: "HELD",
  virtual: false,
  place: EMPTY_PLACE,
  sections: [],
};

function setup(arriving?: boolean) {
  render(
    <QueryClientProvider client={new QueryClient()}>
      <MemoryRouter>
        <ul>
          <MemoryRow
            memory={{ entry: ENTRY, traces: NO_TRACES }}
            label="Frontend Dojo"
            href="/s/7"
            todayIso="2026-01-06T00:00:00.000Z"
            canManage={false}
            {...(arriving !== undefined && { arriving })}
          />
        </ul>
      </MemoryRouter>
    </QueryClientProvider>,
  );
  return screen.getByRole("listitem");
}

describe("MemoryRow", () => {
  it("shows the day and how long ago it was", () => {
    // when
    setup();
    // then
    expect(screen.getAllByText(/Fri/).length).toBeGreaterThan(0);
    expect(screen.getByText("4 days ago")).toBeInTheDocument();
  });

  it("carries the entry instant as data-at", () => {
    // when
    const row = setup();
    // then
    expect(row).toHaveAttribute(
      "data-at",
      String(new Date(SCHEDULED_AT).getTime()),
    );
  });

  it("animates only while arriving", () => {
    // when
    const row = setup(true);
    // then
    expect(row.className).toContain("memory-arrive");
  });

  it("does not animate by default", () => {
    // when
    const row = setup();
    // then
    expect(row.className).not.toContain("memory-arrive");
  });
});
