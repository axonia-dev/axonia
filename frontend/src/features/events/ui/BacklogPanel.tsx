import { AnimatePresence, LayoutGroup, motion } from "framer-motion";
import { type ReactNode } from "react";

import { useTopics } from "@/features/events/api/use-topics";
import type { Topic, TopicKind } from "@/features/events/domain/topic";
import { TopicRow } from "@/features/events/ui/TopicRow";

const LIST_PARAMS = { page: 0, size: 50, status: "PROPOSED" } as const;

interface Props {
  slug: string;
  kind?: TopicKind;
  renderTopicActions?: (topic: Topic) => ReactNode;
}

export function BacklogPanel({
  slug,
  kind,
  renderTopicActions,
}: Readonly<Props>) {
  const topics = useTopics(slug, { ...LIST_PARAMS, ...(kind ? { kind } : {}) });
  const sorted = (topics.data?.content ?? [])
    .slice()
    .sort((a, b) => b.voteCount - a.voteCount);

  if (topics.isLoading) {
    return <BacklogSkeleton />;
  }

  if (topics.isError) {
    return (
      <p role="alert" className="text-danger text-sm">
        Could not load topics.
      </p>
    );
  }

  if (sorted.length === 0) {
    return (
      <p className="text-muted-foreground text-sm">
        No topics yet. Add the first one.
      </p>
    );
  }

  return (
    <LayoutGroup>
      <ul className="flex flex-col gap-2">
        <AnimatePresence initial={false}>
          {sorted.map((topic) => (
            <motion.li
              key={topic.id}
              layout
              initial={{ opacity: 0, y: -8 }}
              animate={{ opacity: 1, y: 0 }}
              exit={{ opacity: 0, y: 8 }}
              transition={{ duration: 0.22, ease: "easeOut" }}
            >
              <TopicRow
                slug={slug}
                topic={topic}
                {...(renderTopicActions
                  ? { actions: renderTopicActions(topic) }
                  : {})}
              />
            </motion.li>
          ))}
        </AnimatePresence>
      </ul>
    </LayoutGroup>
  );
}

function BacklogSkeleton() {
  return (
    <ul className="flex flex-col gap-2">
      {[0, 1, 2].map((i) => (
        <li
          key={i}
          className="bg-surface border-border flex rounded-[var(--radius-md)] border shadow-[var(--shadow-sm)]"
        >
          <div className="border-r-border flex min-w-[52px] flex-col items-center gap-2 border-r px-3 py-4">
            <div className="bg-muted h-5 w-5 animate-pulse rounded" />
            <div className="bg-muted h-4 w-4 animate-pulse rounded" />
          </div>
          <div className="flex flex-1 flex-col gap-2 px-4 py-3">
            <div className="bg-muted h-4 w-2/3 animate-pulse rounded" />
            <div className="bg-muted h-3 w-1/3 animate-pulse rounded" />
            <div className="bg-muted h-3 w-4/5 animate-pulse rounded" />
          </div>
        </li>
      ))}
    </ul>
  );
}
