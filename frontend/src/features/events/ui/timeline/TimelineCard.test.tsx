import { render, screen } from "@testing-library/react";
import { MemoryRouter } from "react-router-dom";
import { describe, expect, it } from "vitest";

import { EMPTY_PLACE } from "@/features/events/domain/meeting-place";
import type { ScheduleEntry } from "@/features/events/domain/schedule";
import type { Slot } from "@/features/events/domain/slot";
import { TimelineCard } from "@/features/events/ui/timeline/TimelineCard";
import type { User } from "@/features/identity/domain/user";

const USERS: Record<string, User> = {
  u1: {
    id: "u1",
    displayName: "Maya Kim",
    memberStatus: "PRESENT",
    avatarIsOwn: false,
    timeZoneIsOwn: false,
  },
  u2: {
    id: "u2",
    displayName: "Jon Reed",
    memberStatus: "PRESENT",
    avatarIsOwn: false,
    timeZoneIsOwn: false,
  },
};

function slot(overrides: Partial<Slot>): Slot {
  return {
    id: 1,
    sectionId: 1,
    position: 0,
    presenterUserIds: [],
    topicId: null,
    title: null,
    notes: null,
    durationMinutes: 15,
    tags: [],
    ...overrides,
  };
}

function entry(overrides: Partial<ScheduleEntry>): ScheduleEntry {
  return {
    sessionId: 1,
    seriesId: 1,
    title: null,
    occurrenceDate: "2026-01-09",
    scheduledAt: "2026-01-09T18:00:00Z",
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

function sections(slots: Slot[]): ScheduleEntry["sections"] {
  return [
    {
      id: 1,
      sessionId: 1,
      position: 0,
      sectionTypeId: null,
      sectionType: null,
      sectionTypeIcon: null,
      sectionTypeAccentColor: null,
      title: null,
      durationMinutes: null,
      slots,
    },
  ];
}

function setup(e: ScheduleEntry, nextUp = false) {
  render(
    <MemoryRouter>
      <TimelineCard
        entry={e}
        label="Frontend Dojo"
        href="/x"
        nextUp={nextUp}
        users={USERS}
      />
    </MemoryRouter>,
  );
}

describe("TimelineCard", () => {
  it("shows title, duration, and open-spot chip on a regular card", () => {
    // given
    const e = entry({
      sections: sections([slot({ presenterUserIds: ["u1"] }), slot({ id: 2 })]),
    });
    // when
    setup(e);
    // then
    expect(screen.getByText("Frontend Dojo")).toBeInTheDocument();
    expect(screen.getByText(/60 min/)).toBeInTheDocument();
    expect(screen.getByText("1 spot open")).toBeInTheDocument();
  });

  it("shows the sneak-peek line only when next up", () => {
    // given
    const e = entry({
      sections: sections([
        slot({ title: "Signals in React 19", presenterUserIds: ["u1"] }),
        slot({ id: 2, title: "Vite plugins", presenterUserIds: ["u2"] }),
      ]),
    });
    // when
    setup(e, true);
    // then
    expect(
      screen.getByText("Signals in React 19 · Maya Kim +1 more talk"),
    ).toBeInTheDocument();
  });

  it("pluralizes the sneak-peek talk count", () => {
    // given
    const e = entry({
      sections: sections([
        slot({ title: "Signals in React 19", presenterUserIds: ["u1"] }),
        slot({ id: 2, title: "Vite plugins", presenterUserIds: ["u2"] }),
        slot({ id: 3, title: "Zod v4", presenterUserIds: ["u2"] }),
      ]),
    });
    // when
    setup(e, true);
    // then
    expect(
      screen.getByText("Signals in React 19 · Maya Kim +2 more talks"),
    ).toBeInTheDocument();
  });

  it("hides the sneak-peek line on non-next cards", () => {
    // given
    const e = entry({
      sections: sections([
        slot({ title: "Signals in React 19", presenterUserIds: ["u1"] }),
      ]),
    });
    // when
    setup(e, false);
    // then
    expect(screen.queryByText(/Signals in React 19/)).not.toBeInTheDocument();
  });

  it("renders the ghost variant with a Projected chip and cadence line", () => {
    // given
    const e = entry({ sessionId: null, virtual: true });
    // when
    setup(e);
    // then
    expect(screen.getByText("Projected")).toBeInTheDocument();
    expect(
      screen.getByText("From the series cadence, not opened yet"),
    ).toBeInTheDocument();
    expect(screen.queryByText("Open for sign-up")).not.toBeInTheDocument();
  });

  it("renders the held variant with a Held chip", () => {
    // given
    const e = entry({ status: "HELD" });
    // when
    setup(e);
    // then
    expect(screen.getByText("Held")).toBeInTheDocument();
  });

  it("invites sign-up when the session has no slots", () => {
    // when
    setup(entry({}));
    // then
    expect(screen.getByText("Open for sign-up")).toBeInTheDocument();
  });

  it("exposes the full context in the accessible name", () => {
    // when
    setup(entry({}));
    // then
    const link = screen.getByRole("link");
    expect(link.getAttribute("aria-label")).toContain("Frontend Dojo");
    expect(link.getAttribute("aria-label")).toContain("January 9");
  });
});
