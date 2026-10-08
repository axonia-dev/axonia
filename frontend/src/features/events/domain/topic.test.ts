import { describe, expect, it } from "vitest";

import { topicSchema } from "@/features/events/domain/topic";

const base = {
  id: 1,
  communityId: 1,
  kind: "REQUEST",
  title: "ES intro",
  description: "please",
  proposerUserId: "user-1",
  proposerDisplayName: "Alice",
  voteCount: 3,
  hasVoted: false,
  canEdit: false,
  canDelete: false,
  status: "PROPOSED",
  createdAt: "2026-06-03T10:00:00Z",
  updatedAt: "2026-06-03T10:00:00Z",
};

describe("topicSchema", () => {
  it("parses a topic", () => {
    const parsed = topicSchema.parse(base);
    expect(parsed.voteCount).toBe(3);
    expect(parsed.kind).toBe("REQUEST");
  });

  it("defaults scheduledFor to null when absent", () => {
    expect(topicSchema.parse(base).scheduledFor).toBeNull();
  });

  it("parses a scheduledFor block", () => {
    const parsed = topicSchema.parse({
      ...base,
      status: "SCHEDULED",
      scheduledFor: {
        sessionId: 12,
        scheduledAt: "2099-06-14T18:00:00Z",
        scheduleLabel: "Frontend Dojo",
      },
    });
    expect(parsed.scheduledFor?.scheduleLabel).toBe("Frontend Dojo");
  });
});
