import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter, Route, Routes } from "react-router-dom";
import { describe, expect, it, vi } from "vitest";

import { EMPTY_PLACE } from "@/features/events/domain/meeting-place";
import type { ScheduleEntry } from "@/features/events/domain/schedule";
import { SessionPanel } from "@/features/events/ui/SessionPanel";
import { morphNames } from "@/test/morph-names";

const ready = vi.hoisted(() => ({
  ready: vi.fn(() => Promise.resolve()),
  prefetch: vi.fn(),
}));

vi.mock("react", async (original) => ({
  ...(await original<typeof import("react")>()),
  ViewTransition: (await import("@/test/morph-names")).ViewTransitionProbe,
}));
vi.mock("@/features/events/api/use-ready-session", () => ({
  useReadySession: () => ready,
}));

const HREF = "/communities/backend-guild/sessions/7";

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

function renderPanel(e: ScheduleEntry, morphId?: string) {
  return render(
    <MemoryRouter initialEntries={["/schedule"]}>
      <Routes>
        <Route
          path="/schedule"
          element={
            <SessionPanel
              entry={e}
              id="calendar-panel"
              label="Guild Sync"
              href={HREF}
              users={{}}
              onClose={vi.fn()}
              {...(morphId !== undefined && { morphId })}
            />
          }
        />
        <Route path={HREF} element={<p>Session page</p>} />
      </Routes>
    </MemoryRouter>,
  );
}

describe("SessionPanel morph", () => {
  it("names its heading as the morph source when given a morph id", () => {
    // when
    const { container } = renderPanel(entry(), "session-7");

    // then
    expect(morphNames(container)).toEqual(["morph-title-session-7"]);
    expect(screen.getByRole("heading", { name: "Guild Sync" })).toContainHTML(
      '<span class="inline-block max-w-full align-top">Guild Sync</span>',
    );
  });

  it("names nothing without a morph id", () => {
    // when
    const { container } = renderPanel(entry());

    // then
    expect(morphNames(container)).toEqual([]);
  });

  it("readies the session on intent and opens it through the morph link", async () => {
    // given
    const user = userEvent.setup({ delay: null });
    renderPanel(entry(), "session-7");
    const link = screen.getByRole("link", { name: /open the session/i });

    // when
    await user.hover(link);
    await user.click(link);

    // then
    expect(ready.prefetch).toHaveBeenCalledWith(7);
    expect(await screen.findByText("Session page")).toBeInTheDocument();
  });
});
