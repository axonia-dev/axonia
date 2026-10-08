import type { DragEndEvent } from "@dnd-kit/core";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { renderHook, waitFor } from "@testing-library/react";
import type { ReactNode } from "react";
import { beforeEach, describe, expect, it, vi } from "vitest";

import { httpEventsGateway } from "@/features/events/adapters/http-events-gateway";
import { EMPTY_PLACE } from "@/features/events/domain/meeting-place";
import type { ScheduleEntry } from "@/features/events/domain/schedule";
import {
  movedTo,
  resolveDrop,
  useCalendarDragReschedule,
  useCalendarReschedule,
} from "@/features/events/ui/CalendarDnd";
import { aScheduledSession, aScheduleEntry } from "@/test/session-fixture";

vi.mock("@/features/events/adapters/http-events-gateway", () => ({
  httpEventsGateway: {
    getSession: vi.fn(),
    ensureOccurrence: vi.fn(),
    updateSession: vi.fn(),
  },
}));

const gateway = vi.mocked(httpEventsGateway);

const SERIES_PLACE = {
  location: "Room 4B, Gasperich",
  joinUrl: "https://meet.example/dojo",
  joiningDetails: "Passcode 4242",
};
const OWN_PLACE = {
  location: "The annex",
  joinUrl: "https://meet.example/this-week",
  joiningDetails: "Passcode 1111",
};

function wrapper({ children }: { children: ReactNode }) {
  const client = new QueryClient({
    defaultOptions: { queries: { retry: false } },
  });
  return <QueryClientProvider client={client}>{children}</QueryClientProvider>;
}

const realEntry = aScheduleEntry({
  sessionId: 42,
  scheduledAt: "2026-07-02T17:00:00Z",
});

const ghostEntry = aScheduleEntry({
  sessionId: null,
  seriesId: 5,
  virtual: true,
});

function dragEnd(entry: ScheduleEntry, overIso: string | null): DragEndEvent {
  return {
    active: { id: "x", data: { current: { entry } } },
    over: overIso
      ? { id: `day:${overIso}`, data: { current: { iso: overIso } } }
      : null,
  } as unknown as DragEndEvent;
}

const oneUpdate = () =>
  waitFor(() => expect(gateway.updateSession.mock.calls).toHaveLength(1));

/** Drops the entry on the target day and settles, which is the whole of the gesture: a drag
 *  commits at once rather than opening anything. */
function drag(entry: ScheduleEntry, targetIso: string) {
  const { result } = renderHook(
    () => useCalendarDragReschedule("backend-guild"),
    { wrapper },
  );
  result.current.mutate({ entry, targetIso });
  return oneUpdate();
}

beforeEach(() => vi.clearAllMocks());

describe("movedTo", () => {
  it("keeps the hour the community meets at, read in its series' zone", () => {
    // given - 18:00Z is 20:00 in Paris, and 11:00 on the viewer's pinned clock
    const session = aScheduledSession();
    // when
    const actual = movedTo(session, "2026-09-01");
    // then
    expect(actual).toEqual({
      date: "2026-09-01",
      time: "20:00",
      zone: "Europe/Paris",
    });
  });

  it("carries an occurrence nobody set an hour for without giving it one", () => {
    // given
    const session = aScheduledSession({ hasTime: false });
    // when
    const actual = movedTo(session, "2026-09-01");
    // then
    expect(actual).toEqual({
      date: "2026-09-01",
      time: null,
      zone: "Europe/Paris",
    });
  });

  it("reads a session with no series on the viewer's clock, the only one it has", () => {
    // given
    const session = aScheduledSession({ seriesId: null, seriesTimeZone: null });
    // when
    const actual = movedTo(session, "2026-09-01");
    // then
    expect(actual).toEqual({
      date: "2026-09-01",
      time: "11:00",
      zone: "America/Los_Angeles",
    });
  });
});

describe("resolveDrop", () => {
  it("returns null when there is no drop target", () => {
    expect(resolveDrop(dragEnd(realEntry, null))).toBeNull();
  });

  it("returns null when dropped on the same day", () => {
    expect(resolveDrop(dragEnd(realEntry, "2026-07-02"))).toBeNull();
  });

  it("returns the entry and target when moved to another day", () => {
    expect(resolveDrop(dragEnd(realEntry, "2026-07-09"))).toEqual({
      entry: realEntry,
      targetIso: "2026-07-09",
    });
  });
});

describe("useCalendarDragReschedule", () => {
  it("reschedules a real session, preserving duration and facilitator", async () => {
    gateway.getSession.mockResolvedValue({
      id: 42,
      scheduledAt: "2026-07-02T17:00:00Z",
      hasTime: true,
      seriesTimeZone: "Europe/Paris",
      durationMinutes: 60,
      facilitatorUserId: "alice",
    } as never);
    gateway.updateSession.mockResolvedValue({ id: 42 } as never);

    await drag(realEntry, "2026-07-09");

    expect(gateway.getSession.mock.calls[0]).toEqual([42]);
    expect(gateway.updateSession.mock.calls[0]).toEqual([
      42,
      {
        scheduledAt: {
          date: "2026-07-09",
          time: "19:00",
          zone: "Europe/Paris",
        },
        durationMinutes: 60,
        facilitatorUserId: "alice",
      },
    ]);
  });

  it("materializes a ghost then reschedules the new session", async () => {
    gateway.ensureOccurrence.mockResolvedValue({
      id: 99,
      scheduledAt: "2026-07-02T18:00:00Z",
      hasTime: true,
      seriesTimeZone: "Europe/Paris",
      durationMinutes: 60,
      facilitatorUserId: null,
    } as never);
    gateway.updateSession.mockResolvedValue({ id: 99 } as never);

    await drag(ghostEntry, "2026-07-09");

    expect(gateway.ensureOccurrence.mock.calls[0]).toEqual([5, "2026-07-02"]);
    expect(gateway.updateSession.mock.calls[0]).toEqual([
      99,
      {
        scheduledAt: {
          date: "2026-07-09",
          time: "20:00",
          zone: "Europe/Paris",
        },
        durationMinutes: 60,
        facilitatorUserId: null,
      },
    ]);
  });

  /**
   * The PUT replaces the session wholesale, so a drag decides the place whether it means to or
   * not. Sending back the resolved `place` would stamp the series' place onto every occurrence
   * anybody ever moved; sending nothing would wipe an override the organizer set.
   */
  it("carries the occurrence's own place across a drag, and never the one it inherits", async () => {
    gateway.getSession.mockResolvedValue({
      id: 42,
      scheduledAt: "2026-07-02T17:00:00Z",
      hasTime: true,
      durationMinutes: 60,
      facilitatorUserId: "alice",
      place: OWN_PLACE,
      placeOverride: OWN_PLACE,
      seriesPlace: SERIES_PLACE,
    } as never);
    gateway.updateSession.mockResolvedValue({ id: 42 } as never);

    await drag(realEntry, "2026-07-09");

    expect(gateway.updateSession.mock.calls[0]?.[1]).toMatchObject({
      place: OWN_PLACE,
    });
  });

  it("leaves an inheriting occurrence inheriting after a drag", async () => {
    gateway.getSession.mockResolvedValue({
      id: 42,
      scheduledAt: "2026-07-02T17:00:00Z",
      hasTime: true,
      durationMinutes: 60,
      facilitatorUserId: "alice",
      place: SERIES_PLACE,
      placeOverride: EMPTY_PLACE,
      seriesPlace: SERIES_PLACE,
    } as never);
    gateway.updateSession.mockResolvedValue({ id: 42 } as never);

    await drag(realEntry, "2026-07-09");

    expect(gateway.updateSession.mock.calls[0]?.[1]).toMatchObject({
      place: EMPTY_PLACE,
    });
  });
});

describe("useCalendarReschedule", () => {
  const confirmed = {
    scheduledAt: { date: "2026-07-09", time: "20:00", zone: "Europe/Paris" },
    durationMinutes: 90,
    facilitatorUserId: "alice",
    place: OWN_PLACE,
  };

  it("writes the confirmed replacement untouched, against the row the chip already has", async () => {
    gateway.updateSession.mockResolvedValue({ id: 42 } as never);

    const { result } = renderHook(
      () => useCalendarReschedule("backend-guild"),
      { wrapper },
    );
    result.current.mutate({ entry: realEntry, input: confirmed });

    await oneUpdate();
    expect(gateway.updateSession.mock.calls[0]).toEqual([42, confirmed]);
    // The dialog was seeded from the row before it opened; nothing here re-reads or rebuilds it.
    expect(gateway.getSession).not.toHaveBeenCalled();
    expect(gateway.ensureOccurrence).not.toHaveBeenCalled();
  });

  /** Materialising on open would leave a row behind for every organizer who opened the dialog on a
   *  ghost and thought better of it. */
  it("materialises a ghost on confirm and never before it", async () => {
    gateway.ensureOccurrence.mockResolvedValue({ id: 99 } as never);
    gateway.updateSession.mockResolvedValue({ id: 99 } as never);

    const { result } = renderHook(
      () => useCalendarReschedule("backend-guild"),
      { wrapper },
    );
    expect(gateway.ensureOccurrence).not.toHaveBeenCalled();

    result.current.mutate({ entry: ghostEntry, input: confirmed });

    await oneUpdate();
    expect(gateway.ensureOccurrence.mock.calls[0]).toEqual([5, "2026-07-02"]);
    expect(gateway.updateSession.mock.calls[0]).toEqual([99, confirmed]);
  });
});
