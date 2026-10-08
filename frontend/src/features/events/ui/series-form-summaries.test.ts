import { describe, expect, it } from "vitest";

import {
  placeIsBlank,
  placeSummary,
  structureSummary,
} from "@/features/events/ui/series-form-summaries";

function draft(over: Partial<Record<string, string>> = {}) {
  return { location: "", joinUrl: "", joiningDetails: "", ...over };
}

describe("placeIsBlank", () => {
  it("counts a field holding only spaces as untouched", () => {
    expect(placeIsBlank(draft({ location: "   " }))).toBe(true);
    expect(placeIsBlank(draft({ location: "Room 4B" }))).toBe(false);
  });

  /** All-or-nothing: any one part filled in is a place, which is what the shut section has to
   *  say and what decides whether it opens on an edit. */
  it("counts any one filled part as a place", () => {
    expect(placeIsBlank(draft({ joiningDetails: "Passcode 42" }))).toBe(false);
  });
});

describe("placeSummary", () => {
  it("says nothing is set, and stops there", () => {
    expect(placeSummary(draft())).toBe("Not set.");
  });

  it("names the room when there is one", () => {
    expect(placeSummary(draft({ location: "Room 4B, Gasperich" }))).toBe(
      "Room 4B, Gasperich",
    );
  });

  /** A link with no room name still says something locating: which service the session meets on.
   *  The whole URL would be a line of opaque token in a one-line summary. */
  it("falls back to the host of the join link", () => {
    expect(
      placeSummary(draft({ joinUrl: "https://teams.microsoft.com/l/abc123" })),
    ).toBe("teams.microsoft.com");
  });

  /** Summarised while it is still being typed, so half a URL is not a host to report. */
  it("does not report a host for a link that is not one yet", () => {
    expect(placeSummary(draft({ joinUrl: "https:/" }))).toBe(
      "Joining details only.",
    );
  });

  it("says so when only the joining details were filled in", () => {
    expect(placeSummary(draft({ joiningDetails: "Passcode 42" }))).toBe(
      "Joining details only.",
    );
  });
});

describe("structureSummary", () => {
  it("says there is nothing yet", () => {
    expect(structureSummary([])).toBe("No sections yet.");
  });

  it("counts the sections and the minutes they claim", () => {
    expect(
      structureSummary([
        { sectionTypeId: 1, title: null, durationMinutes: 15 },
        { sectionTypeId: 2, title: null, durationMinutes: 45 },
      ]),
    ).toBe("2 sections, 60 min");
  });

  it("keeps one section singular", () => {
    expect(
      structureSummary([
        { sectionTypeId: 1, title: null, durationMinutes: 15 },
      ]),
    ).toBe("1 section, 15 min");
  });

  /** A section with no length of its own counts for nothing rather than reporting "0 min", which
   *  reads as a section that takes no time. */
  it("reports the count alone when no section has a length", () => {
    expect(
      structureSummary([
        { sectionTypeId: 1, title: null, durationMinutes: null },
      ]),
    ).toBe("1 section");
  });
});
