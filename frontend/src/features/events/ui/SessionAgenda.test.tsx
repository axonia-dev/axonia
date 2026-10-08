import { render, screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";

import { EMPTY_PLACE } from "@/features/events/domain/meeting-place";
import type { ScheduleEntry } from "@/features/events/domain/schedule";
import type { Slot } from "@/features/events/domain/slot";
import { SessionAgenda } from "@/features/events/ui/SessionAgenda";

function slot(overrides: Partial<Slot> = {}): Slot {
  return {
    id: 1,
    sectionId: 1,
    position: 0,
    presenterUserIds: [],
    topicId: null,
    title: null,
    notes: null,
    durationMinutes: 20,
    tags: [],
    ...overrides,
  };
}

function entryWith(sections: ScheduleEntry["sections"]): ScheduleEntry {
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
    sections,
  };
}

const TALKS = {
  id: 1,
  sessionId: 7,
  position: 0,
  sectionTypeId: 1,
  sectionType: "Talks",
  sectionTypeIcon: "mic",
  sectionTypeAccentColor: "#ff5c97",
  title: null,
  durationMinutes: 40,
  slots: [
    slot({
      id: 1,
      title: "Testing the untestable",
      presenterUserIds: ["u1"],
      tags: ["testing"],
    }),
    slot({ id: 2, title: null, presenterUserIds: [] }),
  ],
};

describe("SessionAgenda", () => {
  it("groups the agenda under the section it belongs to", () => {
    render(<SessionAgenda entry={entryWith([TALKS])} users={{}} />);
    expect(screen.getByText("Talks")).toBeInTheDocument();
    expect(screen.getByText("Testing the untestable")).toBeInTheDocument();
  });

  it("shows an unclaimed slot as an open spot rather than hiding it", () => {
    render(<SessionAgenda entry={entryWith([TALKS])} users={{}} />);
    expect(screen.getByText("Open spot")).toBeInTheDocument();
  });

  it("states how long each slot runs", () => {
    render(<SessionAgenda entry={entryWith([TALKS])} users={{}} />);
    expect(screen.getAllByText("20 min")).toHaveLength(2);
  });

  it("names a section with no slots as a round table", () => {
    render(
      <SessionAgenda
        entry={entryWith([{ ...TALKS, sectionType: "Open floor", slots: [] }])}
        users={{}}
      />,
    );
    expect(screen.getByText(/round table/i)).toBeInTheDocument();
  });

  it("renders nothing at all when there is no agenda", () => {
    const { container } = render(
      <SessionAgenda entry={entryWith([])} users={{}} />,
    );
    expect(container).toBeEmptyDOMElement();
  });

  it("shows a slot's tags", () => {
    render(<SessionAgenda entry={entryWith([TALKS])} users={{}} />);
    expect(screen.getByText("testing")).toBeInTheDocument();
  });
});
