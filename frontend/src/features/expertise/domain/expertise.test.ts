import { describe, expect, it } from "vitest";

import {
  areaEvidencePageSchema,
  topicMapSchema,
} from "@/features/expertise/domain/expertise";

describe("evidence schema", () => {
  it("accepts a closed action item, which the backend does emit", () => {
    const page = {
      items: [
        {
          signalType: "COMPLETED",
          sourceType: "ACTION_ITEM",
          sourceId: 7,
          label: "Write the migration runbook",
          communitySlug: "backend-guild",
          occurredAt: "2026-09-01T10:00:00Z",
        },
      ],
      hasMore: false,
    };

    expect(() => areaEvidencePageSchema.parse(page)).not.toThrow();
  });
});

describe("topic map schema", () => {
  const map = { topics: [], links: [] };

  it("carries the named areas the backend sends beside the topics", () => {
    const namedAreas = [{ id: 3, name: "Streaming", tags: ["kafka"] }];

    expect(topicMapSchema.parse({ ...map, namedAreas }).namedAreas).toEqual(
      namedAreas,
    );
  });

  // The two apps deploy apart: a map read from a backend a release behind must still draw, with nothing named.
  it("reads a map without named areas as one where nothing is named", () => {
    expect(topicMapSchema.parse(map).namedAreas).toEqual([]);
  });

  it("rejects a named area without a name", () => {
    expect(() =>
      topicMapSchema.parse({ ...map, namedAreas: [{ id: 3, tags: [] }] }),
    ).toThrow();
  });
});
