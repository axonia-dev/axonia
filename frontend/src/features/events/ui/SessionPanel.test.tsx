import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter } from "react-router-dom";
import { beforeEach, describe, expect, it, vi } from "vitest";

import {
  OPEN_MINUTES,
  SOON_MINUTES,
} from "@/features/events/domain/join-window";
import { EMPTY_PLACE } from "@/features/events/domain/meeting-place";
import type { ScheduleEntry } from "@/features/events/domain/schedule";
import { SessionPanel } from "@/features/events/ui/SessionPanel";

let user: ReturnType<typeof userEvent.setup>;

beforeEach(() => {
  user = userEvent.setup({ delay: null });
});

const JOIN_URL = "https://meet.example/x";
const DIAL_IN = "Dial +352 27 00 00\nID 123 456 789";

/** An instant that falls inside the "soon" window: past the point the way in could still be
 *  called far off, short of the point it opens. Derived from `join-window.ts`'s own constants
 *  rather than a hardcoded minute offset, so it cannot silently drift into the wrong phase if
 *  those constants change. */
function soonInstant(scheduledAt: string): Date {
  const start = Date.parse(scheduledAt);
  const midpoint = Math.floor((SOON_MINUTES + OPEN_MINUTES) / 2);
  return new Date(start - midpoint * 60_000);
}

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

function renderPanel(
  e: ScheduleEntry,
  extra: Partial<{ now: Date; href: string | null }> = {},
) {
  const onClose = vi.fn();
  render(
    <MemoryRouter>
      <SessionPanel
        entry={e}
        id="calendar-panel"
        label="Guild Sync"
        href={
          extra.href === undefined
            ? "/communities/backend-guild/sessions/7"
            : extra.href
        }
        users={{}}
        onClose={onClose}
        {...(extra.now && { now: extra.now })}
      />
    </MemoryRouter>,
  );
  return onClose;
}

describe("SessionPanel", () => {
  it("names the day and the hours the session runs between", () => {
    renderPanel(entry());
    // The day line is formatted with the runtime's own locale, so assert the parts
    // rather than an order only en-GB produces.
    const day = screen.getByTestId("panel-day");
    expect(day).toHaveTextContent(/thursday/i);
    expect(day).toHaveTextContent(/august/i);
    expect(day).toHaveTextContent(/20/);
    expect(screen.getByTestId("panel-when")).toHaveTextContent(
      /\d{2}:\d{2} - \d{2}:\d{2}/,
    );
  });

  it("says an occurrence nobody set an hour for has no time yet", () => {
    renderPanel(entry({ hasTime: false, scheduledAt: "2026-08-19T22:00:00Z" }));
    expect(screen.getByTestId("panel-when")).toHaveTextContent(
      /time to be confirmed/i,
    );
  });

  it("offers the way in once the session is running", () => {
    renderPanel(
      entry({ place: { ...EMPTY_PLACE, joinUrl: "https://meet.example/x" } }),
      { now: new Date("2026-08-20T12:10:00Z") },
    );
    expect(screen.getByRole("link", { name: /join now/i })).toBeInTheDocument();
  });

  /**
   * Shut, but stated. The panel is opened to ask about one session, so a way in that is simply
   * absent reads as a session nobody can get into rather than one whose door is not open yet.
   */
  it("withholds the way in well before the session, and says when it opens", () => {
    renderPanel(
      entry({ place: { ...EMPTY_PLACE, joinUrl: "https://meet.example/x" } }),
      { now: new Date("2026-08-18T12:00:00Z") },
    );
    expect(
      screen.queryByRole("link", { name: /join now/i }),
    ).not.toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Join" })).toHaveAttribute(
      "aria-disabled",
      "true",
    );
    expect(
      screen.getByText("The way in opens five minutes before the session."),
    ).toBeInTheDocument();
  });

  it("says nothing at all about the way into a session that has already run", () => {
    renderPanel(
      entry({ place: { ...EMPTY_PLACE, joinUrl: "https://meet.example/x" } }),
      { now: new Date("2026-08-21T12:00:00Z") },
    );
    expect(
      screen.queryByRole("button", { name: "Join" }),
    ).not.toBeInTheDocument();
    expect(screen.queryByText(/five minutes before/)).not.toBeInTheDocument();
  });

  it("shows the session is coming up but keeps the way in shut during the soon window", () => {
    renderPanel(
      entry({ place: { ...EMPTY_PLACE, joinUrl: "https://meet.example/x" } }),
      { now: soonInstant("2026-08-20T12:00:00Z") },
    );
    expect(screen.getByText(/starting soon/i)).toBeInTheDocument();
    expect(
      screen.queryByRole("link", { name: /join now/i }),
    ).not.toBeInTheDocument();
  });

  it("states where the session meets", () => {
    renderPanel(
      entry({ place: { ...EMPTY_PLACE, location: "Room B2, Zurich" } }),
    );
    expect(screen.getByText("Room B2, Zurich")).toBeInTheDocument();
  });

  it("states the joining details a guest still needs", () => {
    renderPanel(entry({ place: { ...EMPTY_PLACE, joiningDetails: DIAL_IN } }));
    expect(screen.getByText(/Dial \+352 27 00 00/)).toBeInTheDocument();
  });

  /**
   * The same rule the session page applies, on the same predicate. A passcode for a session that
   * was cancelled, skipped or closed out is a spent credential with nothing left to open, while
   * the location is not a credential at all: "this met in Room 4B" stays worth reading afterwards.
   */
  it.each(["CANCELLED", "SKIPPED", "HELD"] as const)(
    "leaves the spent joining details off a %s session",
    (status) => {
      renderPanel(
        entry({
          status,
          place: {
            ...EMPTY_PLACE,
            location: "Room 4B",
            joiningDetails: DIAL_IN,
          },
        }),
      );

      expect(screen.queryByText(/Dial \+352 27 00 00/)).not.toBeInTheDocument();
      expect(screen.getByText("Room 4B")).toBeInTheDocument();
    },
  );

  /**
   * `JoinControl` is the single authority on the way in, and it withholds the link until five
   * minutes before the start and takes it away once the session is no longer to happen. A plain
   * anchor printing the URL beside it would hand out the way into a session two weeks off, and
   * print the same address twice while one is running.
   */
  it.each([
    ["two days ahead", new Date("2026-08-18T12:00:00Z")],
    ["minutes before the start", soonInstant("2026-08-20T12:00:00Z")],
    ["while the session is running", new Date("2026-08-20T12:10:00Z")],
    ["long after it ended", new Date("2026-08-21T12:00:00Z")],
  ] as const)("never prints the way in itself, %s", (_phase, now) => {
    renderPanel(entry({ place: { ...EMPTY_PLACE, joinUrl: JOIN_URL } }), {
      now,
    });

    expect(screen.queryByText(JOIN_URL)).not.toBeInTheDocument();
    expect(
      screen.queryByRole("link", { name: JOIN_URL }),
    ).not.toBeInTheDocument();
  });

  it("links through to the session itself", () => {
    renderPanel(entry());
    expect(
      screen.getByRole("link", { name: /open the session/i }),
    ).toHaveAttribute("href", "/communities/backend-guild/sessions/7");
  });

  it("explains a projection and does not pretend it is a session", () => {
    renderPanel(entry({ virtual: true, sessionId: null }), { href: null });
    expect(
      screen.getByText(/from the series cadence, not opened yet/i),
    ).toBeInTheDocument();
    expect(
      screen.queryByRole("link", { name: /open the session/i }),
    ).not.toBeInTheDocument();
  });

  it("labels a projection's footer link as opening the preview, not the session", () => {
    const previewHref =
      "/communities/backend-guild/series/1/occurrences/2026-08-20";
    renderPanel(entry({ virtual: true, sessionId: null }), {
      href: previewHref,
    });
    expect(
      screen.getByRole("link", { name: /open the preview/i }),
    ).toHaveAttribute("href", previewHref);
    expect(
      screen.queryByRole("link", { name: /open the session/i }),
    ).not.toBeInTheDocument();
  });

  it("closes when Escape is pressed", async () => {
    const onClose = renderPanel(entry());
    await user.keyboard("{Escape}");
    expect(onClose).toHaveBeenCalledTimes(1);
  });

  it("closes when the close control is used", async () => {
    const onClose = renderPanel(entry());
    await user.click(screen.getByRole("button", { name: /close/i }));
    expect(onClose).toHaveBeenCalledTimes(1);
  });
});
