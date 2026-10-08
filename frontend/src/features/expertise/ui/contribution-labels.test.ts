import { describe, expect, it } from "vitest";

import type {
  MapContribution,
  MapPerson,
} from "@/features/expertise/domain/expertise";
import {
  authorOf,
  labelsFor,
} from "@/features/expertise/ui/contribution-labels";

function contribution(
  overrides: Partial<MapContribution> = {},
  evidence: Partial<MapContribution["evidence"]> = {},
): MapContribution {
  return {
    signalId: 1,
    canonicalTag: "kafka",
    userId: "u-1",
    reach: null,
    ...overrides,
    evidence: {
      signalType: "PRESENTED",
      sourceType: "SLOT",
      sourceId: 1,
      label: "Scaling Kafka",
      communitySlug: "platform-guild",
      occurredAt: "2026-08-01T00:00:00Z",
      sessionId: null,
      typeId: null,
      typeLabel: null,
      typeIcon: null,
      typeAccentColor: null,
      ...evidence,
    },
  };
}

const ada: MapPerson = {
  userId: "u-1",
  displayName: "Ada Lovelace",
  avatarUrl: null,
};

const people = new Map([[ada.userId, ada]]);

describe("labelsFor", () => {
  it("names the contribution, what kind of thing it was, and who did it", () => {
    const actual = labelsFor([contribution()], people);

    expect(actual.get(1)).toBe("Scaling Kafka, a talk by Ada Lovelace");
  });

  it.each([
    ["ARTIFACT", "an artifact"],
    ["TOPIC", "a topic"],
    ["ACTION_ITEM", "an action item"],
  ] as const)("says what a %s is in words", (sourceType, words) => {
    const actual = labelsFor(
      [contribution({}, { sourceType, label: "Something" })],
      people,
    );

    expect(actual.get(1)).toBe(`Something, ${words} by Ada Lovelace`);
  });

  it("falls back to the area when the source carried no title", () => {
    const actual = labelsFor([contribution({}, { label: null })], people);

    expect(actual.get(1)).toBe("kafka, a talk by Ada Lovelace");
  });

  it("names a contributor the payload left out as the rest of the app does, never by a raw id", () => {
    const actual = labelsFor([contribution({ userId: "u-9" })], people);

    expect(actual.get(1)).toBe("Scaling Kafka, a talk by Deleted member");
  });
});

describe("authorOf", () => {
  it("finds the person a contribution belongs to", () => {
    expect(authorOf(contribution(), people)).toBe(ada);
  });

  it("has no author for a contribution nobody is selected on", () => {
    expect(authorOf(null, people)).toBeNull();
  });

  it("has no author for a contributor the payload left out", () => {
    expect(authorOf(contribution({ userId: "u-9" }), people)).toBeNull();
  });
});
