import { describe, expect, it } from "vitest";

import { EMPTY_PLACE } from "@/features/events/domain/meeting-place";
import { seriesSchema } from "@/features/events/domain/series";

const validSeriesJson = {
  id: 1,
  communityId: 1,
  title: "Frontend Dojo",
  cadence: { count: 1, unit: "WEEK" },
  startsOn: "2026-06-15",
  preferredTime: "18:00:00",
  timeZone: "Europe/Zurich",
  durationMinutes: 60,
  place: EMPTY_PLACE,
  status: "ACTIVE",
  defaultStructure: [
    { sectionTypeId: 1, title: "Main talk", durationMinutes: 30 },
  ],
  nextSuggestedAt: "2026-06-15T18:00:00Z",
  createdAt: "x",
  updatedAt: "x",
};

const populatedPlace = {
  location: "Room 4B",
  joinUrl: "https://meet.example/dojo",
  joiningDetails: "ID 391 2244",
};

describe("seriesSchema", () => {
  it("parses the backend shape", () => {
    expect(seriesSchema.parse(validSeriesJson).defaultStructure[0]?.title).toBe(
      "Main talk",
    );
  });

  it("rejects the pre-remodel shape (type/preferredWeekday)", () => {
    const old = { ...validSeriesJson, type: "DOJO", preferredWeekday: 3 };
    // unknown keys are stripped, but the missing-field contract still holds:
    expect(() => seriesSchema.parse({ ...old, startsOn: undefined })).toThrow();
  });

  it("parses a nested cadence", () => {
    const parsed = seriesSchema.parse({
      ...validSeriesJson,
      cadence: { count: 3, unit: "WEEK" },
    });
    expect(parsed.cadence).toEqual({ count: 3, unit: "WEEK" });
  });

  it("rejects a cadence count below 1", () => {
    expect(() =>
      seriesSchema.parse({
        ...validSeriesJson,
        cadence: { count: 0, unit: "WEEK" },
      }),
    ).toThrow();
  });

  it("parses a place carrying a location, join link and joining details", () => {
    const parsed = seriesSchema.parse({
      ...validSeriesJson,
      place: populatedPlace,
    });
    expect(parsed.place).toEqual(populatedPlace);
    expect(parsed.place.joinUrl).toBe("https://meet.example/dojo");
  });
});
