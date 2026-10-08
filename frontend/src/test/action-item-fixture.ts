import type { ActionItem } from "@/features/events/domain/action-item";

/** An open action point raised at session 42, assigned to Bob, answered at no session yet. */
export function anActionItem(overrides: Partial<ActionItem> = {}): ActionItem {
  return {
    id: 101,
    communityId: 1,
    title: "Write up the rebalance runbook",
    description: "",
    tags: [],
    raisedByUserId: "alice",
    raisedByDisplayName: "Alice",
    assigneeUserId: "bob",
    assigneeDisplayName: "Bob Green",
    dueDate: null,
    originSessionId: 42,
    checkpoint: {
      sessionId: null,
      scheduledAt: null,
      pinned: false,
      governingTimeZone: null,
    },
    carryCount: 0,
    outcome: "OPEN",
    canClose: true,
    canReopen: false,
    canEdit: false,
    canDelete: false,
    createdAt: "x",
    updatedAt: "x",
    ...overrides,
  };
}
