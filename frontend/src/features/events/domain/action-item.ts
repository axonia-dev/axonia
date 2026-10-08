import { z } from "zod";

import { startOfTodayUtc } from "@/features/events/domain/schedule-window";

const actionItemOutcomeSchema = z.enum(["OPEN", "DONE", "DROPPED"]);
export type ActionItemOutcome = z.infer<typeof actionItemOutcomeSchema>;

const checkpointOutcomeSchema = z.enum(["DONE", "CARRIED", "DROPPED"]);
export type CheckpointOutcome = z.infer<typeof checkpointOutcomeSchema>;

const nullableString = z
  .string()
  .nullish()
  .transform((v) => v ?? null);
const nullableNumber = z
  .number()
  .nullish()
  .transform((v) => v ?? null);

/** Where an item is answered. A null `sessionId` means the community has no session to point at
 *  yet, not that the item is unbound - every item owes a checkpoint. `pinned` is false when it
 *  late-binds to whatever session comes next, true when an organizer named a specific one. */
const checkpointSchema = z.object({
  sessionId: nullableNumber,
  scheduledAt: nullableString,
  pinned: z.boolean(),
  /** The zone the checkpoint session's wall clock runs on, already resolved server-side from the
   *  series' zone or a one-off's own. Null when the session names neither. */
  governingTimeZone: nullableString.default(null),
});

export const actionItemSchema = z.object({
  id: z.number(),
  communityId: z.number(),
  title: z.string(),
  description: z.string(),
  tags: z.array(z.string()).default([]),
  raisedByUserId: z.string(),
  raisedByDisplayName: z.string(),
  assigneeUserId: nullableString,
  assigneeDisplayName: nullableString,
  dueDate: nullableString,
  originSessionId: nullableNumber,
  checkpoint: checkpointSchema,
  carryCount: z.number().default(0),
  outcome: actionItemOutcomeSchema,
  canClose: z.boolean().default(false),
  canReopen: z.boolean().default(false),
  canEdit: z.boolean().default(false),
  canDelete: z.boolean().default(false),
  createdAt: z.string(),
  updatedAt: z.string(),
});

export type ActionItem = z.infer<typeof actionItemSchema>;

/**
 * Whether a due date has passed. Stated once because two surfaces render the same item's due
 * date side by side.
 *
 * A `dueDate` is a civil date, not an instant: `new Date("2026-09-30")` is UTC midnight, so
 * comparing it against a local `now` calls the item overdue from mid-afternoon the day before
 * for every viewer west of UTC, and lets it read as due all through the due day for every viewer
 * east of it. Today's UTC start is the only boundary that holds for both.
 */
export function isDueDateOverdue(dueDate: string, now: Date): boolean {
  return new Date(dueDate).toISOString() < startOfTodayUtc(now);
}

/**
 * Done and dropped items read back as one list, newest-settled first.
 *
 * The list endpoint narrows to a single outcome, so the two buckets arrive as two pages, each
 * already ordered by `updated_at` server-side. This interleaves them on that same key - a merge of
 * two sorted pages, not a re-sort of one, which is why the order shown and the order fetched are
 * the same column.
 */
export function settledOrder(items: readonly ActionItem[]): ActionItem[] {
  return items.slice().sort((a, b) => b.updatedAt.localeCompare(a.updatedAt));
}

/** `assignee` is a free-text reference to the member the work is dispatched to: blank or
 *  omitted leaves it unassigned. `checkpointSessionId` pins the session it is answered at;
 *  omitted, it late-binds to whichever session comes next. `originSessionId` is history only,
 *  set once at raise and never editable afterwards. */
export interface RaiseActionItemInput {
  title: string;
  description?: string;
  tags?: string[];
  assignee?: string | null;
  dueDate?: string | null;
  originSessionId?: number;
  checkpointSessionId?: number | null;
}

/** Re-pins or clears the checkpoint: an explicit `null` late-binds to whichever session comes
 *  next, the same as omitting the key. `originSessionId` has no place here - it is not
 *  editable. */
export interface UpdateActionItemInput {
  title: string;
  description?: string;
  tags?: string[];
  assignee?: string | null;
  dueDate?: string | null;
  checkpointSessionId?: number | null;
}
