import { describe, expect, it } from "vitest";

import {
  artifactSchema,
  isArchived,
} from "@/features/knowledge/domain/artifact";

const active = {
  id: 1,
  communityId: 2,
  typeId: 3,
  type: "Recording",
  title: "Hexagonal architecture talk",
  description: "Recording of the June dojo",
  url: "https://example.com/talk",
  tags: ["architecture", "hexagonal"],
  contributorUserId: "alice",
  loveCount: 4,
  provenance: { sessionId: 9, segmentId: null, label: "June dojo" },
  status: "ACTIVE",
  createdAt: "2026-06-06T10:00:00Z",
  updatedAt: "2026-06-06T10:00:00Z",
};

describe("artifactSchema", () => {
  it("parses an active artifact with provenance", () => {
    // when
    const actual = artifactSchema.parse(active);

    // then
    expect(actual.title).toBe("Hexagonal architecture talk");
    expect(actual.provenance?.sessionId).toBe(9);
  });

  it("parses an artifact with null provenance", () => {
    // given
    const standalone = { ...active, provenance: null };

    // when
    const actual = artifactSchema.parse(standalone);

    // then
    expect(actual.provenance).toBeNull();
  });

  it("rejects an unknown status", () => {
    // given
    const invalid = { ...active, status: "DRAFT" };

    // when / then
    expect(() => artifactSchema.parse(invalid)).toThrow();
  });
});

describe("isArchived", () => {
  it("is true only for archived artifacts", () => {
    // given
    const archived = artifactSchema.parse({ ...active, status: "ARCHIVED" });

    // when / then
    expect(isArchived(archived)).toBe(true);
    expect(isArchived(artifactSchema.parse(active))).toBe(false);
  });
});
