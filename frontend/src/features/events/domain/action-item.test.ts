import { describe, expect, it } from "vitest";

import {
  actionItemSchema,
  isDueDateOverdue,
  settledOrder,
} from "@/features/events/domain/action-item";
import { anActionItem } from "@/test/action-item-fixture";

const base = {
  id: 1,
  communityId: 7,
  title: "Close the Jira ticket",
  description: "",
  tags: [],
  raisedByUserId: "u1",
  raisedByDisplayName: "Ada",
  carryCount: 0,
  outcome: "OPEN",
  canClose: true,
  canReopen: false,
  canEdit: true,
  canDelete: true,
  createdAt: "2026-09-19T08:00:00Z",
  updatedAt: "2026-09-19T08:00:00Z",
};

describe("actionItemSchema", () => {
  it("reads an unpinned checkpoint as the next session", () => {
    const parsed = actionItemSchema.parse({
      ...base,
      checkpoint: {
        sessionId: 12,
        scheduledAt: "2026-09-24T09:00:00Z",
        pinned: false,
      },
    });

    expect(parsed.checkpoint.pinned).toBe(false);
    expect(parsed.assigneeUserId).toBeNull();
    expect(parsed.dueDate).toBeNull();
  });

  it("reads a community with no upcoming session as an unresolved checkpoint", () => {
    const parsed = actionItemSchema.parse({
      ...base,
      checkpoint: { sessionId: null, scheduledAt: null, pinned: false },
    });

    expect(parsed.checkpoint.sessionId).toBeNull();
  });

  it("reads a pinned checkpoint that keeps its session even in the past", () => {
    const parsed = actionItemSchema.parse({
      ...base,
      checkpoint: {
        sessionId: 3,
        scheduledAt: "2026-01-01T09:00:00Z",
        pinned: true,
      },
    });

    expect(parsed.checkpoint.pinned).toBe(true);
  });
});

/**
 * A due date is a civil date, not an instant, and two surfaces render the same item's date side
 * by side. Read against a local `now` it is overdue from mid-afternoon the day before west of
 * UTC and still due all through the due day east of it, so the two disagree on one screen.
 */
describe("isDueDateOverdue", () => {
  it("is not overdue while the due day is still running somewhere west of UTC", () => {
    // given the evening before the due date, on a clock west of UTC
    const now = new Date("2026-09-30T01:00:00Z");

    // when
    const actual = isDueDateOverdue("2026-09-30", now);

    // then
    expect(actual).toBe(false);
  });

  it("is not overdue during the due day itself", () => {
    const actual = isDueDateOverdue(
      "2026-09-30",
      new Date("2026-09-30T23:00:00Z"),
    );

    expect(actual).toBe(false);
  });

  it("is overdue once the due day has passed", () => {
    const actual = isDueDateOverdue(
      "2026-09-30",
      new Date("2026-10-01T00:00:00Z"),
    );

    expect(actual).toBe(true);
  });
});

describe("settledOrder", () => {
  /** Done and dropped arrive as two separate pages, each in the server's own creation order. Read
   *  back as one record of finished work, the question is when work settled, not when it started. */
  it("reads the two settled buckets back newest-settled first", () => {
    // given
    const items = [
      anActionItem({
        id: 1,
        outcome: "DONE",
        updatedAt: "2026-09-10T09:00:00Z",
      }),
      anActionItem({
        id: 2,
        outcome: "DROPPED",
        updatedAt: "2026-09-18T09:00:00Z",
      }),
      anActionItem({
        id: 3,
        outcome: "DONE",
        updatedAt: "2026-09-14T09:00:00Z",
      }),
    ];

    // when
    const actual = settledOrder(items);

    // then
    expect(actual.map((item) => item.id)).toEqual([2, 3, 1]);
  });

  it("leaves the list it was given untouched", () => {
    // given
    const items = [
      anActionItem({ id: 1, updatedAt: "2026-09-10T09:00:00Z" }),
      anActionItem({ id: 2, updatedAt: "2026-09-18T09:00:00Z" }),
    ];

    // when
    settledOrder(items);

    // then
    expect(items.map((item) => item.id)).toEqual([1, 2]);
  });
});
