import { EmptyState } from "@/components/patterns/EmptyState";
import { useActionItems } from "@/features/events/api/use-action-items";
import type { ActionItem } from "@/features/events/domain/action-item";
import type { ActionItemListParams } from "@/features/events/domain/events-gateway";
import { ActionItemActions } from "@/features/events/ui/ActionItemActions";
import {
  CarryChip,
  CheckpointStatusChip,
  DueChip,
} from "@/features/events/ui/ActionItemChips";
import { AssigneeBadge } from "@/features/events/ui/AssigneeBadge";
import { checkpointDayLabel } from "@/features/events/ui/checkpoint-dates";
import { TopicListCard } from "@/features/events/ui/TopicListCard";
import { TruncationNotice } from "@/features/events/ui/TruncationNotice";
import { useNow } from "@/lib/use-wake-at";

/** `sort` is the server's own "checkpoint date then due date", which is the order this tab
 *  groups in: without it the page is the 20 most recently created, and the item pinned to
 *  tomorrow's session is not on it at all. */
const LIST_PARAMS: ActionItemListParams = {
  outcome: "OPEN",
  sort: "checkpoint",
  page: 0,
  size: 50,
};

interface ActionItemGroup {
  key: string;
  label: string;
  items: ActionItem[];
}

function dateSortKey(value: string | null): number {
  if (value === null) return Number.POSITIVE_INFINITY;
  return new Date(value).getTime();
}

function byDueDate(a: ActionItem, b: ActionItem): number {
  return dateSortKey(a.dueDate) - dateSortKey(b.dueDate);
}

function scheduledAtOf(items: ActionItem[]): string | null {
  const [first] = items;
  return first ? first.checkpoint.scheduledAt : null;
}

interface SessionBuckets {
  sessions: Map<number, ActionItem[]>;
  unresolved: ActionItem[];
}

function bucketBySession(items: ActionItem[]): SessionBuckets {
  const sessions = new Map<number, ActionItem[]>();
  const unresolved: ActionItem[] = [];
  for (const item of items) {
    const sessionId = item.checkpoint.sessionId;
    if (sessionId === null) {
      unresolved.push(item);
      continue;
    }
    const bucket = sessions.get(sessionId);
    if (bucket) {
      bucket.push(item);
    } else {
      sessions.set(sessionId, [item]);
    }
  }
  return { sessions, unresolved };
}

function sessionDateLabel(
  scheduledAt: string | null,
  governingTimeZone: string | null,
): string {
  if (scheduledAt === null) return "Not yet scheduled";
  return checkpointDayLabel(scheduledAt, governingTimeZone);
}

/**
 * Open items bucketed by the session that will answer them, soonest first, ordered by
 * `checkpoint.scheduledAt` then `dueDate`. `checkpoint.sessionId === null` late-binds to
 * whichever session comes next rather than sitting unbound, so those items share one
 * trailing group instead of scattering across the list.
 */
function groupActionItems(items: ActionItem[]): ActionItemGroup[] {
  const { sessions, unresolved } = bucketBySession(items);

  const sessionGroups = [...sessions.entries()]
    .map(([sessionId, groupItems]) => ({
      key: `session-${sessionId}`,
      scheduledAt: scheduledAtOf(groupItems),
      // Every item in the bucket answers at the same session, so any one of them names its zone.
      governingTimeZone: groupItems[0]?.checkpoint.governingTimeZone ?? null,
      items: groupItems.slice().sort(byDueDate),
    }))
    .sort((a, b) => dateSortKey(a.scheduledAt) - dateSortKey(b.scheduledAt))
    .map((group) => ({
      key: group.key,
      label: sessionDateLabel(group.scheduledAt, group.governingTimeZone),
      items: group.items,
    }));

  if (unresolved.length === 0) {
    return sessionGroups;
  }

  return [
    ...sessionGroups,
    {
      key: "unresolved",
      label: "Not yet scheduled",
      items: unresolved.slice().sort(byDueDate),
    },
  ];
}

/** A checkpoint's `scheduledAt` is an instant, unlike a due date, so it is compared as one. */
function isPast(instant: string | null, now: Date): boolean {
  if (instant === null) return false;
  return new Date(instant).getTime() < now.getTime();
}

interface CardProps {
  slug: string;
  item: ActionItem;
  now: Date;
}

function ActionItemCard({ slug, item, now }: Readonly<CardProps>) {
  const unresolvedCheckpoint = item.checkpoint.sessionId === null;
  const unreportedCheckpoint =
    !unresolvedCheckpoint && isPast(item.checkpoint.scheduledAt, now);

  return (
    <TopicListCard
      className="group relative"
      title={item.title}
      meta={
        <>
          <DueChip dueDate={item.dueDate} now={now} />
          <CheckpointStatusChip
            unresolved={unresolvedCheckpoint}
            unreported={unreportedCheckpoint}
          />
          <CarryChip carryCount={item.carryCount} />
        </>
      }
      trailing={
        <>
          <AssigneeBadge name={item.assigneeDisplayName} />
          <ActionItemActions slug={slug} item={item} />
        </>
      }
    />
  );
}

interface GroupProps {
  slug: string;
  group: ActionItemGroup;
  now: Date;
}

function ActionItemGroupSection({ slug, group, now }: Readonly<GroupProps>) {
  return (
    <div role="group" aria-label={group.label} className="flex flex-col gap-2">
      <h3 className="text-muted-foreground text-xs font-semibold tracking-wide uppercase">
        {group.label}
      </h3>
      <ul className="flex flex-col gap-2">
        {group.items.map((item) => (
          <li key={item.id}>
            <ActionItemCard slug={slug} item={item} now={now} />
          </li>
        ))}
      </ul>
    </div>
  );
}

function ActionItemsSkeleton() {
  return (
    <div className="flex flex-col gap-2">
      {[0, 1, 2].map((i) => (
        <div
          key={i}
          className="bg-surface border-border rounded-[var(--radius-md)] border px-4 py-3 shadow-[var(--shadow-sm)]"
        >
          <div className="bg-muted h-4 w-2/3 animate-pulse rounded" />
          <div className="bg-muted mt-2 h-3 w-1/3 animate-pulse rounded" />
        </div>
      ))}
    </div>
  );
}

export function ActionItemsTab({ slug }: Readonly<{ slug: string }>) {
  const actionItems = useActionItems(slug, LIST_PARAMS);
  const now = useNow();

  if (actionItems.isLoading) {
    return <ActionItemsSkeleton />;
  }

  if (actionItems.isError) {
    return (
      <p role="alert" className="text-danger text-sm">
        Could not load action items.
      </p>
    );
  }

  const items = actionItems.data?.content ?? [];

  if (items.length === 0) {
    return (
      <EmptyState
        title="No open action items."
        description="Work raised in a session lands here until it is closed."
      />
    );
  }

  const groups = groupActionItems(items);
  const totalCount = actionItems.data?.totalElements ?? items.length;

  return (
    <div className="flex flex-col gap-6">
      {groups.map((group) => (
        <ActionItemGroupSection
          key={group.key}
          slug={slug}
          group={group}
          now={now}
        />
      ))}
      <TruncationNotice shown={items.length} total={totalCount} />
    </div>
  );
}
