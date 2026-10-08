import { useSettledActionItems } from "@/features/events/api/use-action-items";
import type { ActionItem } from "@/features/events/domain/action-item";
import { outcomeLabel } from "@/features/events/ui/action-item-labels";
import { AssigneeBadge } from "@/features/events/ui/AssigneeBadge";
import { DateTile } from "@/features/events/ui/DateTile";
import { TopicListCard } from "@/features/events/ui/TopicListCard";
import { TruncationNotice } from "@/features/events/ui/TruncationNotice";
import { cn } from "@/lib/cn";

const chipClass =
  "inline-flex items-center gap-1 rounded px-1.5 py-0.5 text-[10px] font-semibold";

/** Done and "won’t do" are two different records, not one "closed": the first says the work
 *  landed, the second that the community settled it as work it will not do. */
function OutcomeChip({
  outcome,
}: Readonly<{ outcome: ActionItem["outcome"] }>) {
  const done = outcome === "DONE";
  return (
    <span
      className={cn(
        chipClass,
        done ? "bg-success/15 text-success" : "bg-muted text-muted-foreground",
      )}
    >
      {outcomeLabel(outcome)}
    </span>
  );
}

function SettledRow({ item }: Readonly<{ item: ActionItem }>) {
  return (
    <TopicListCard
      leading={<DateTile iso={item.updatedAt} />}
      title={item.title}
      meta={
        <>
          <OutcomeChip outcome={item.outcome} />
          <AssigneeBadge name={item.assigneeDisplayName} />
        </>
      }
    />
  );
}

/**
 * Every action item the community has settled, closed and dropped alike, under Past - the tab a
 * member already opens to find finished work. Without it a settled item is reachable only from
 * the session that raised it, and an item raised outside any session is reachable from nowhere.
 */
export function SettledActionItems({ slug }: Readonly<{ slug: string }>) {
  const settled = useSettledActionItems(slug);

  if (settled.isLoading) {
    return null;
  }
  if (settled.isError) {
    return (
      <p role="alert" className="text-danger text-sm">
        Could not load settled action items.
      </p>
    );
  }
  if (settled.items.length === 0) {
    return null;
  }

  return (
    <section aria-label="Settled action items" className="flex flex-col gap-2">
      <h3 className="text-muted-foreground text-xs font-semibold tracking-wide uppercase">
        Settled action items
      </h3>
      <ul className="flex flex-col gap-2">
        {settled.items.map((item) => (
          <li key={item.id}>
            <SettledRow item={item} />
          </li>
        ))}
      </ul>
      <TruncationNotice
        shown={settled.items.length}
        total={settled.total ?? settled.items.length}
      />
    </section>
  );
}
