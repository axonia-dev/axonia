import { describe, expect, it } from "vitest";

import { latestArtifacts } from "@/features/community/domain/library";
import type { Artifact } from "@/features/knowledge/domain/artifact";

function anArtifact(overrides: Partial<Artifact> = {}): Artifact {
  return {
    id: 42,
    typeId: 3,
    type: "RFC",
    title: "An RFC",
    contributorUserId: "u1",
    loveCount: 5,
    createdAt: "2024-01-01T00:00:00Z",
    tags: [],
    ...overrides,
  } as unknown as Artifact;
}

describe("latestArtifacts", () => {
  it("builds a focus deep-link and carries type visuals and love", () => {
    const [row] = latestArtifacts("guild", [anArtifact()]);

    expect(row).toMatchObject({
      href: "/communities/guild/library?focus=42",
      loveCount: 5,
      typeId: 3,
      type: "RFC",
      contributorUserId: "u1",
    });
  });

  it("puts the newest first", () => {
    const rows = latestArtifacts("guild", [
      anArtifact({ id: 1, createdAt: "2024-01-01T00:00:00Z" }),
      anArtifact({ id: 2, createdAt: "2026-01-01T00:00:00Z" }),
    ]);

    expect(rows.map((row) => row.key)).toEqual(["artifact-2", "artifact-1"]);
  });

  it("honours the limit", () => {
    const rows = latestArtifacts(
      "guild",
      [anArtifact({ id: 1 }), anArtifact({ id: 2 })],
      1,
    );

    expect(rows).toHaveLength(1);
  });
});
