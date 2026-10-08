import { describe, expect, it } from "vitest";

import {
  canDetachResource,
  type SessionResource,
} from "@/features/knowledge/domain/session-resource";

function originOnlyResource(
  contributorUserId: string | undefined,
): SessionResource {
  return {
    attachmentId: null,
    segmentId: null,
    attachedByUserId: null,
    artifact: {
      id: 9,
      communityId: 1,
      typeId: 4,
      type: "Slides",
      title: "React 19 deck",
      description: "",
      url: "https://x.dev/deck",
      tags: [],
      contributorUserId,
      loveCount: 0,
      provenance: null,
      status: "ACTIVE",
      createdAt: "x",
      updatedAt: "x",
    },
  } as unknown as SessionResource;
}

describe("canDetachResource", () => {
  it("withholds detach from an anonymous caller even with nothing to compare against", () => {
    const resource = originOnlyResource(undefined);

    expect(canDetachResource(resource, false, undefined)).toBe(false);
  });
});
