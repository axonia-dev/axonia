import type { Journal } from "@/features/mentoring/domain/mentoring";
import { cn } from "@/lib/cn";

interface Props {
  journal: Journal;
  topicIds: number[];
  /** Set where the chips stand on the warm ticket, which would swallow a warm chip. */
  onWarm?: boolean;
}

function chipTint(done: boolean, onWarm: boolean): string {
  if (done) return "bg-[color-mix(in_srgb,var(--success)_22%,var(--surface))]";
  return onWarm
    ? "bg-surface"
    : "bg-[color-mix(in_srgb,var(--warning)_20%,var(--surface))]";
}

/** The board topics behind these ids, as chips; nothing when none of them is on the board. */
export function TopicChips({
  journal,
  topicIds,
  onWarm = false,
}: Readonly<Props>) {
  const named = topicIds
    .map((id) => journal.topics.find((topic) => topic.id === id))
    .filter((topic) => topic !== undefined);
  if (named.length === 0) return null;
  return (
    <ul aria-label="Topics" className="flex min-w-0 flex-wrap gap-1.5">
      {named.map((topic) => (
        <li
          key={topic.id}
          className={cn(
            "text-foreground inline-flex max-w-full items-center gap-1 rounded-full px-2.5 py-0.5 text-[11px] font-semibold",
            chipTint(topic.state === "DONE", onWarm),
          )}
        >
          <span aria-hidden>✦</span>
          <span className="truncate">{topic.label}</span>
        </li>
      ))}
    </ul>
  );
}

/** A planned meeting's topics, or a quiet line while it names none. */
export function PlannedTopics({
  journal,
  topicIds,
  onWarm = false,
}: Readonly<Props>) {
  const named = topicIds.some((id) =>
    journal.topics.some((topic) => topic.id === id),
  );
  if (!named) {
    return <p className="text-muted-foreground text-xs italic">No topic yet</p>;
  }
  return <TopicChips journal={journal} topicIds={topicIds} onWarm={onWarm} />;
}
