import { describe, expect, it } from "vitest";

import { groupTopicsBySession } from "@/features/events/domain/group-topics-by-session";
import type { Topic } from "@/features/events/domain/topic";

const now = new Date("2026-07-22T10:00:00-07:00");

function topic(id: number, scheduledFor: Topic["scheduledFor"]): Topic {
  return {
    id,
    communityId: 1,
    kind: "OFFER",
    title: `Topic ${id}`,
    description: "",
    tags: [],
    proposerUserId: "user-1",
    proposerDisplayName: "Alice",
    canClose: false,
    canReopen: false,
    voteCount: 0,
    hasVoted: false,
    canEdit: false,
    canDelete: false,
    status: "SCHEDULED",
    scheduledFor,
    createdAt: "2026-01-01T00:00:00Z",
    updatedAt: "2026-01-01T00:00:00Z",
  };
}

function slot(sessionId: number, scheduledAt: string) {
  return { sessionId, scheduledAt, scheduleLabel: "Frontend Dojo" };
}

describe("groupTopicsBySession", () => {
  it("buckets topics under their session, soonest session first", () => {
    const groups = groupTopicsBySession(
      [
        topic(1, slot(20, "2026-08-06T18:00:00Z")),
        topic(2, slot(10, "2026-07-23T18:00:00Z")),
        topic(3, slot(10, "2026-07-23T18:00:00Z")),
      ],
      now,
    );

    expect(groups.map((g) => g.sessionId)).toEqual([10, 20]);
    expect(groups[0]?.topics.map((t) => t.id)).toEqual([2, 3]);
    expect(groups[0]?.scheduleLabel).toBe("Frontend Dojo");
  });

  it("drops past sessions and unscheduled topics", () => {
    const groups = groupTopicsBySession(
      [
        topic(1, slot(10, "2000-01-01T18:00:00Z")),
        topic(2, null),
        topic(3, slot(20, "2026-07-23T18:00:00Z")),
      ],
      now,
    );

    expect(groups.map((g) => g.sessionId)).toEqual([20]);
  });
});
