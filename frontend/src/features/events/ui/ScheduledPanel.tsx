import { AnimatePresence, LayoutGroup, motion } from "framer-motion";
import { CalendarClock } from "lucide-react";
import type { ReactNode } from "react";
import { Link } from "react-router-dom";

import { EmptyState } from "@/components/patterns/EmptyState";
import { useTopics } from "@/features/events/api/use-topics";
import {
  groupTopicsBySession,
  type SessionGroup,
} from "@/features/events/domain/group-topics-by-session";
import { relativeDayLabel } from "@/features/events/domain/relative-day-label";
import type { Topic } from "@/features/events/domain/topic";
import { PresenterHoverCard } from "@/features/events/ui/PresenterHoverCard";
import { TopicKindBadge } from "@/features/events/ui/TopicKindBadge";
import { TopicListCard } from "@/features/events/ui/TopicListCard";
import { useUsers } from "@/features/identity/api/use-users";
import type { User } from "@/features/identity/domain/user";
import { cn } from "@/lib/cn";
import { useNow } from "@/lib/use-wake-at";

const LIST_PARAMS = { page: 0, size: 50, status: "SCHEDULED" } as const;

function proposerIdsOf(groups: SessionGroup[]): string[] {
  const ids = groups.flatMap((group) =>
    group.topics.map((topic) => topic.proposerUserId),
  );
  return [...new Set(ids)].sort((a, b) => a.localeCompare(b));
}

function CountdownChip({ scheduledAt }: Readonly<{ scheduledAt: string }>) {
  const { label, soon } = relativeDayLabel(useNow(), new Date(scheduledAt));
  return (
    <span
      className={cn(
        "rounded-full px-2 py-0.5 text-[11px] font-semibold",
        soon ? "bg-primary/10 text-primary" : "bg-muted text-muted-foreground",
      )}
    >
      {label}
    </span>
  );
}

function SessionNode({
  slug,
  group,
  isNext,
  users,
  renderScheduledActions,
}: Readonly<{
  slug: string;
  group: SessionGroup;
  isNext: boolean;
  users: Record<string, User>;
  renderScheduledActions?: (topic: Topic) => ReactNode;
}>) {
  const date = new Date(group.scheduledAt).toLocaleDateString(undefined, {
    weekday: "short",
    day: "numeric",
    month: "short",
  });
  return (
    <li className="timeline-row relative">
      <span
        aria-hidden="true"
        className={cn(
          "absolute top-1 -left-6 h-3.5 w-3.5 rounded-full border-2",
          isNext
            ? "timeline-beacon border-primary bg-primary"
            : "border-border-strong bg-background",
        )}
      />
      <div className="mb-2 flex flex-wrap items-center gap-2">
        <Link
          to={`/communities/${slug}/sessions/${group.sessionId}`}
          className="text-foreground hover:text-primary text-sm font-bold underline-offset-2 hover:underline"
        >
          {date}
        </Link>
        <span className="text-muted-foreground text-xs">
          {group.scheduleLabel}
        </span>
        <CountdownChip scheduledAt={group.scheduledAt} />
      </div>
      <ul className="flex flex-col gap-2">
        <AnimatePresence initial={false}>
          {group.topics.map((topic) => (
            <motion.li
              key={topic.id}
              layout
              initial={{ opacity: 0, y: -8 }}
              animate={{ opacity: 1, y: 0 }}
              exit={{ opacity: 0, y: 8 }}
              transition={{ duration: 0.22, ease: "easeOut" }}
            >
              <TopicListCard
                title={topic.title}
                meta={
                  <>
                    <TopicKindBadge kind={topic.kind} />
                    <span className="text-muted-foreground text-xs">
                      {topic.proposerDisplayName}
                    </span>
                  </>
                }
                trailing={
                  <>
                    <PresenterHoverCard
                      name={topic.proposerDisplayName}
                      user={users[topic.proposerUserId]}
                      align="end"
                    />
                    {renderScheduledActions?.(topic)}
                  </>
                }
              />
            </motion.li>
          ))}
        </AnimatePresence>
      </ul>
    </li>
  );
}

function ScheduledSkeleton() {
  return (
    <ol className="flex flex-col gap-6 pl-6">
      {[0, 1].map((i) => (
        <li key={i} className="flex flex-col gap-2">
          <div className="bg-muted h-4 w-48 animate-pulse rounded" />
          <div className="bg-muted h-14 animate-pulse rounded-[var(--radius-md)]" />
        </li>
      ))}
    </ol>
  );
}

export function ScheduledPanel({
  slug,
  renderScheduledActions,
}: Readonly<{
  slug: string;
  renderScheduledActions?: (topic: Topic) => ReactNode;
}>) {
  const topics = useTopics(slug, LIST_PARAMS);
  const groups = groupTopicsBySession(topics.data?.content ?? [], useNow());
  const users = useUsers(proposerIdsOf(groups)).data ?? {};

  if (topics.isLoading) {
    return <ScheduledSkeleton />;
  }
  if (topics.isError) {
    return (
      <p role="alert" className="text-danger text-sm">
        Could not load topics.
      </p>
    );
  }
  if (groups.length === 0) {
    return (
      <EmptyState
        icon={<CalendarClock />}
        title="Nothing scheduled yet"
        description="Pick a topic from the backlog and put it on a session."
      />
    );
  }

  return (
    <LayoutGroup>
      <ol className="before:bg-border relative flex flex-col gap-6 pl-6 before:absolute before:top-1.5 before:bottom-1.5 before:left-[5px] before:w-0.5 before:rounded-full">
        {groups.map((group, index) => (
          <SessionNode
            key={group.sessionId}
            slug={slug}
            group={group}
            isNext={index === 0}
            users={users}
            {...(renderScheduledActions ? { renderScheduledActions } : {})}
          />
        ))}
      </ol>
    </LayoutGroup>
  );
}
