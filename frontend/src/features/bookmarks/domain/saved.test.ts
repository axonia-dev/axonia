import { describe, expect, it } from "vitest";

import {
  savedArtifactViewSchema,
  savedItemSchema,
  saveStateSchema,
} from "@/features/bookmarks/domain/saved";

const artifact = {
  id: 1,
  communityId: 2,
  typeId: 3,
  type: "RFC",
  title: "Virtual threads",
  description: "",
  url: "https://example.com/x",
  tags: ["java"],
  contributorUserId: "alice",
  loveCount: 0,
  provenance: null,
  status: "ACTIVE",
  createdAt: "2026-06-01T10:00:00Z",
  updatedAt: "2026-06-01T10:00:00Z",
};

describe("saveStateSchema", () => {
  it("parses an unanswered saved state", () => {
    const actual = saveStateSchema.parse({
      viewerHasSaved: true,
      viewerHelped: null,
    });
    expect(actual.viewerHasSaved).toBe(true);
    expect(actual.viewerHelped).toBeNull();
  });

  it("parses an answered helped state", () => {
    const actual = saveStateSchema.parse({
      viewerHasSaved: true,
      viewerHelped: false,
    });
    expect(actual.viewerHelped).toBe(false);
  });
});

describe("savedArtifactViewSchema", () => {
  it("parses a library row carrying its artifact", () => {
    const actual = savedArtifactViewSchema.parse({
      kind: "artifact",
      artifact,
      community: null,
      savedAt: "2026-06-02T00:00:00Z",
      helped: null,
    });
    expect(actual.artifact.title).toBe("Virtual threads");
    expect(actual.helped).toBeNull();
  });

  it("parses a row carrying its community", () => {
    const actual = savedArtifactViewSchema.parse({
      kind: "artifact",
      artifact,
      community: {
        id: 2,
        slug: "backend-guild",
        name: "Backend Guild",
        accentColor: "#5B8DEF",
      },
      savedAt: "2026-06-02T00:00:00Z",
      helped: null,
    });
    expect(actual.community?.name).toBe("Backend Guild");
  });

  it("parses a row whose community can no longer be resolved", () => {
    const actual = savedArtifactViewSchema.parse({
      kind: "artifact",
      artifact,
      community: null,
      savedAt: "2026-06-02T00:00:00Z",
      helped: null,
    });
    expect(actual.community).toBeNull();
  });
});

describe("savedItemSchema", () => {
  it("parses an artifact item via the kind discriminator", () => {
    const parsed = savedItemSchema.parse({
      kind: "artifact",
      artifact,
      community: { id: 1, slug: "c", name: "C", accentColor: null },
      savedAt: "2026-07-01T00:00:00Z",
      helped: null,
    });
    expect(parsed.kind).toBe("artifact");
  });

  it("parses a session item", () => {
    const parsed = savedItemSchema.parse({
      kind: "session",
      session: {
        id: 5,
        communityId: 1,
        title: "Retro",
        status: "HELD",
        scheduledAt: "2026-06-23T14:00:00Z",
        occurrenceDate: null,
        facilitatorUserId: "alice",
        slotCount: 3,
        recordingUrl: null,
      },
      community: { id: 1, slug: "c", name: "C", accentColor: null },
      savedAt: "2026-07-02T00:00:00Z",
    });
    expect(parsed.kind === "session" && parsed.session.title).toBe("Retro");
  });
});
