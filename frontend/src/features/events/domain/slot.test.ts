import { describe, expect, it } from "vitest";

import { isOpen, slotSchema } from "@/features/events/domain/slot";

const raw = {
  id: 1,
  sectionId: 3,
  position: 0,
  presenterUserIds: ["alice", "bob"],
  topicId: 7,
  title: "Intro",
  notes: null,
  durationMinutes: 20,
  tags: [],
};

describe("slotSchema", () => {
  it("parses a filled slot with presenters and a topic", () => {
    const slot = slotSchema.parse(raw);
    expect(slot.presenterUserIds).toEqual(["alice", "bob"]);
    expect(slot.topicId).toBe(7);
  });

  it("parses an open slot with no presenters and a null title", () => {
    const slot = slotSchema.parse({
      ...raw,
      presenterUserIds: [],
      topicId: null,
      title: null,
    });
    expect(slot.presenterUserIds).toEqual([]);
    expect(slot.title).toBeNull();
  });
});

describe("slotSchema durationMinutes", () => {
  it("parses a slot with a duration", () => {
    const slot = slotSchema.parse({
      id: 1,
      sectionId: 2,
      position: 0,
      presenterUserIds: [],
      topicId: null,
      title: null,
      notes: null,
      durationMinutes: 20,
      tags: [],
    });
    expect(slot.durationMinutes).toBe(20);
  });
});

describe("isOpen", () => {
  it("is true when a slot has no presenters", () => {
    expect(isOpen(slotSchema.parse({ ...raw, presenterUserIds: [] }))).toBe(
      true,
    );
  });

  it("is false when a slot has at least one presenter", () => {
    expect(isOpen(slotSchema.parse(raw))).toBe(false);
  });
});
