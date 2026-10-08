import { EMPTY_PLACE } from "@/features/events/domain/meeting-place";
import type { ScheduleEntry } from "@/features/events/domain/schedule";
import type { Series } from "@/features/events/domain/series";
import { aScheduleEntry } from "@/test/session-fixture";

/** A weekly series, whose title is the only fact a schedule view reads off it. */
export const SERIES_LIST: Series[] = [
  {
    id: 1,
    communityId: 10,
    title: "Backend Guild Series",
    cadence: { count: 1, unit: "WEEK" },
    startsOn: "2026-01-01",
    preferredTime: null,
    timeZone: "Europe/Zurich",
    durationMinutes: null,
    status: "ACTIVE",
    place: EMPTY_PLACE,
    defaultStructure: [],
    nextSuggestedAt: null,
    createdAt: "x",
    updatedAt: "x",
  },
];

/** An occurrence of that series that already happened. */
export const heldSeriesEntry: ScheduleEntry = aScheduleEntry({
  sessionId: 1,
  occurrenceDate: "2026-06-05",
  scheduledAt: "2026-06-05T18:00:00Z",
  status: "HELD",
});

/** The same occurrence a week on, never opened: a ghost the series projects rather than a row. */
export const ghostSeriesEntry: ScheduleEntry = {
  ...heldSeriesEntry,
  sessionId: null,
  occurrenceDate: "2026-06-12",
  scheduledAt: "2026-06-12T18:00:00Z",
  status: "SCHEDULED",
  virtual: true,
};

/** A session outside any series, with one presenter booked and one slot still open. */
export const oneOffEntry: ScheduleEntry = aScheduleEntry({
  sessionId: 99,
  seriesId: null,
  title: "Special Session",
  occurrenceDate: "2026-06-20",
  scheduledAt: "2026-06-20T14:00:00Z",
  durationMinutes: 45,
  sections: [
    {
      id: 7,
      sessionId: 99,
      position: 0,
      sectionTypeId: null,
      sectionType: null,
      sectionTypeIcon: null,
      sectionTypeAccentColor: null,
      title: null,
      durationMinutes: null,
      slots: [
        {
          id: 70,
          sectionId: 7,
          position: 0,
          presenterUserIds: ["u1"],
          topicId: null,
          title: "Event sourcing 101",
          notes: null,
          durationMinutes: 30,
          tags: [],
        },
        {
          id: 71,
          sectionId: 7,
          position: 1,
          presenterUserIds: [],
          topicId: null,
          title: null,
          notes: null,
          durationMinutes: 15,
          tags: [],
        },
      ],
    },
  ],
});

/** Called off, so no schedule view should give it a row. */
export const cancelledEntry: ScheduleEntry = {
  ...heldSeriesEntry,
  sessionId: 2,
  occurrenceDate: "2026-06-19",
  scheduledAt: "2026-06-19T18:00:00Z",
  status: "CANCELLED",
};
