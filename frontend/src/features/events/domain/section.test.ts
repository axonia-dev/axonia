import { describe, expect, it } from "vitest";

import {
  isRoundTable,
  sectionAccentColor,
  sectionLabel,
  sectionSchema,
  sectionUsedMinutes,
} from "@/features/events/domain/section";

const slot = {
  id: 1,
  sectionId: 3,
  position: 0,
  presenterUserIds: ["alice"],
  topicId: null,
  title: "Pairing on hexagons",
  notes: null,
  durationMinutes: 20,
  tags: [],
};

const raw = {
  id: 3,
  sessionId: 9,
  position: 0,
  sectionTypeId: 2,
  sectionType: "Talk",
  sectionTypeIcon: null,
  sectionTypeAccentColor: null,
  title: "Main talk",
  durationMinutes: 30,
  slots: [slot],
};

describe("sectionSchema", () => {
  it("parses a section with nested slots", () => {
    const section = sectionSchema.parse(raw);
    expect(section.slots).toHaveLength(1);
    expect(section.slots[0]?.presenterUserIds).toEqual(["alice"]);
  });

  it("parses a round-table section with no slots and a null sectionType", () => {
    const section = sectionSchema.parse({
      ...raw,
      sectionTypeId: null,
      sectionType: null,
      title: null,
      durationMinutes: null,
      slots: [],
    });
    expect(section.slots).toEqual([]);
    expect(section.sectionType).toBeNull();
  });
});

describe("sectionUsedMinutes", () => {
  it("sums the slot durations", () => {
    const section = {
      id: 1,
      sessionId: 1,
      position: 0,
      sectionTypeId: 1,
      sectionType: "Talk",
      sectionTypeIcon: null,
      sectionTypeAccentColor: null,
      title: null,
      durationMinutes: 40,
      tags: [],
      slots: [
        {
          id: 1,
          sectionId: 1,
          position: 0,
          presenterUserIds: [],
          topicId: null,
          title: "a",
          notes: null,
          durationMinutes: 20,
          tags: [],
        },
        {
          id: 2,
          sectionId: 1,
          position: 1,
          presenterUserIds: [],
          topicId: null,
          title: "b",
          notes: null,
          durationMinutes: 15,
          tags: [],
        },
      ],
    };
    expect(sectionUsedMinutes(section)).toBe(35);
  });
});

describe("isRoundTable", () => {
  it("is true when a section has zero slots", () => {
    expect(isRoundTable(sectionSchema.parse({ ...raw, slots: [] }))).toBe(true);
  });

  it("is false when a section has at least one slot", () => {
    expect(isRoundTable(sectionSchema.parse(raw))).toBe(false);
  });
});

describe("sectionAccentColor", () => {
  it("reads the section type's own accent", () => {
    expect(sectionAccentColor({ sectionTypeAccentColor: "#abc123" })).toBe(
      "#abc123",
    );
  });

  it("falls back to the shared default when the type carries none", () => {
    expect(sectionAccentColor({ sectionTypeAccentColor: null })).toBe(
      "var(--primary)",
    );
  });
});

describe("sectionLabel", () => {
  it("names a section by its type", () => {
    expect(sectionLabel({ sectionType: "Deep Dive", title: null })).toBe(
      "Deep Dive",
    );
  });

  it("falls back to a custom title when the type is unset", () => {
    expect(sectionLabel({ sectionType: null, title: "Kickoff" })).toBe(
      "Kickoff",
    );
  });

  it("falls back to a plain label when neither is set", () => {
    expect(sectionLabel({ sectionType: null, title: null })).toBe("Section");
  });
});
