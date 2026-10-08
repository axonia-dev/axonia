import { AnimatePresence, motion } from "framer-motion";
import { History } from "lucide-react";
import { useState } from "react";
import { Link } from "react-router-dom";

import { EmptyState } from "@/components/patterns/EmptyState";
import { Pagination } from "@/components/patterns/Pagination";
import { SearchField } from "@/components/ui/SearchField";
import { usePastTopics } from "@/features/events/api/use-topics";
import type { Topic } from "@/features/events/domain/topic";
import { DateTile } from "@/features/events/ui/DateTile";
import { SettledActionItems } from "@/features/events/ui/SettledActionItems";
import { TopicKindBadge } from "@/features/events/ui/TopicKindBadge";
import { TopicListCard } from "@/features/events/ui/TopicListCard";

const PAGE_SIZE = 20;

function heldAt(topic: Topic): string {
  return topic.scheduledFor?.scheduledAt ?? topic.updatedAt;
}

function PastTopicMeta({
  slug,
  topic,
}: Readonly<{ slug: string; topic: Topic }>) {
  return (
    <>
      <TopicKindBadge kind={topic.kind} />
      {topic.scheduledFor && (
        <Link
          to={`/communities/${slug}/sessions/${topic.scheduledFor.sessionId}`}
          className="text-muted-foreground hover:text-primary text-xs underline-offset-2 hover:underline"
        >
          {topic.scheduledFor.scheduleLabel}
        </Link>
      )}
      {topic.voteCount > 0 && (
        <span className="text-muted-foreground text-xs">
          {topic.voteCount} {topic.voteCount === 1 ? "vote" : "votes"} when held
        </span>
      )}
    </>
  );
}

function PastTopicsList({
  slug,
  topics,
  isLoading,
  isError,
}: Readonly<{
  slug: string;
  topics: Topic[];
  isLoading: boolean;
  isError: boolean;
}>) {
  if (isLoading) {
    return <PastSkeleton />;
  }
  if (isError) {
    return (
      <p role="alert" className="text-danger text-sm">
        Could not load topics.
      </p>
    );
  }
  if (topics.length === 0) {
    return (
      <EmptyState
        icon={<History />}
        title="No past topics yet"
        description="Topics land here after their session is held."
      />
    );
  }

  return (
    <ul className="flex flex-col gap-2">
      <AnimatePresence initial={false}>
        {topics.map((topic) => (
          <motion.li
            key={topic.id}
            layout
            exit={{ opacity: 0, y: 8 }}
            transition={{ duration: 0.22, ease: "easeOut" }}
            className="timeline-row"
          >
            <TopicListCard
              leading={<DateTile iso={heldAt(topic)} />}
              title={topic.title}
              meta={<PastTopicMeta slug={slug} topic={topic} />}
            />
          </motion.li>
        ))}
      </AnimatePresence>
    </ul>
  );
}

function PastSkeleton() {
  return (
    <ul className="flex flex-col gap-2">
      {[0, 1, 2].map((i) => (
        <li
          key={i}
          className="bg-surface border-border flex items-center gap-3 rounded-[var(--radius-md)] border px-4 py-3"
        >
          <div className="bg-muted h-10 w-11 animate-pulse rounded-lg" />
          <div className="flex flex-1 flex-col gap-2">
            <div className="bg-muted h-4 w-2/3 animate-pulse rounded" />
            <div className="bg-muted h-3 w-1/3 animate-pulse rounded" />
          </div>
        </li>
      ))}
    </ul>
  );
}

/** The Past tab: the community's finished work - topics held at a session, and the action items
 *  it has settled since. */
export function PastTopicsPanel({ slug }: Readonly<{ slug: string }>) {
  const [q, setQ] = useState("");
  const [page, setPage] = useState(0);

  const params = {
    page,
    size: PAGE_SIZE,
    ...(q.trim() !== "" ? { q: q.trim() } : {}),
  };

  const topics = usePastTopics(slug, params);
  const data = topics.data;
  const totalPages = data?.totalPages ?? 0;

  return (
    <div className="flex flex-col gap-3">
      <SearchField
        label="Search past topics"
        value={q}
        onChange={(value) => {
          setQ(value);
          setPage(0);
        }}
        placeholder="Search past topics…"
      />

      <PastTopicsList
        slug={slug}
        topics={data?.content ?? []}
        isLoading={topics.isLoading}
        isError={topics.isError}
      />

      {totalPages > 1 && (
        <Pagination
          page={page}
          totalPages={totalPages}
          onPageChange={setPage}
        />
      )}

      <SettledActionItems slug={slug} />
    </div>
  );
}
