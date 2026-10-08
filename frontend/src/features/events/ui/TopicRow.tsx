import { useIsMutating } from "@tanstack/react-query";
import { ChevronDown, ChevronUp, Pencil, Trash2 } from "lucide-react";
import { type ReactNode, useState } from "react";

import { IconButton } from "@/components/ui/IconButton";
import { topicMutationKeys } from "@/features/events/api/query-keys";
import { useUnvoteTopic, useVoteTopic } from "@/features/events/api/use-topics";
import type { Topic } from "@/features/events/domain/topic";
import { DeleteTopicPopover } from "@/features/events/ui/DeleteTopicPopover";
import { EditTopicDialog } from "@/features/events/ui/EditTopicDialog";
import { TopicKindBadge } from "@/features/events/ui/TopicKindBadge";
import { cn } from "@/lib/cn";

// ── File-private sub-components ───────────────────────────────────────────────

interface VoteZoneProps {
  topic: Topic;
  isPending: boolean;
  voteAnim: boolean;
  countAnim: boolean;
  onToggle: () => void;
}

function VoteZone({
  topic,
  isPending,
  voteAnim,
  countAnim,
  onToggle,
}: Readonly<VoteZoneProps>) {
  return (
    <button
      className={cn(
        "flex min-w-[52px] cursor-pointer flex-col items-center justify-start gap-1 rounded-l-[var(--radius-md)] border-r px-3 py-4 transition-colors",
        "border-r-[var(--border)]",
        topic.hasVoted ? "bg-primary/5 hover:bg-primary/10" : "hover:bg-muted",
      )}
      onClick={onToggle}
      disabled={isPending}
      aria-label={
        topic.hasVoted
          ? `Remove vote from ${topic.title}`
          : `Upvote ${topic.title}`
      }
    >
      <ChevronUp
        className={cn(
          "h-5 w-5 transition-colors",
          topic.hasVoted ? "text-primary" : "text-muted-foreground",
          voteAnim && "motion-safe:animate-vote-bounce",
        )}
      />
      <span
        className={cn(
          "text-sm font-semibold tabular-nums transition-colors",
          topic.hasVoted ? "text-primary" : "text-muted-foreground",
          countAnim && "motion-safe:animate-count-pop",
        )}
      >
        {topic.voteCount}
      </span>
    </button>
  );
}

interface HoverActionsProps {
  topic: Topic;
  onEdit: () => void;
  onDelete: () => void;
}

function HoverActions({
  topic,
  onEdit,
  onDelete,
}: Readonly<HoverActionsProps>) {
  return (
    <div className="flex shrink-0 gap-1 opacity-0 transition-opacity group-hover:opacity-100 focus-within:opacity-100">
      {topic.canEdit && (
        <IconButton label={`Edit ${topic.title}`} onPress={onEdit}>
          <Pencil />
        </IconButton>
      )}
      {topic.canDelete && (
        <IconButton
          tone="danger"
          label={`Delete ${topic.title}`}
          onPress={onDelete}
        >
          <Trash2 />
        </IconButton>
      )}
    </div>
  );
}

// ── Main export ───────────────────────────────────────────────────────────────

interface Props {
  slug: string;
  topic: Topic;
  actions?: ReactNode;
}

export function TopicRow({ slug, topic, actions }: Readonly<Props>) {
  const vote = useVoteTopic(slug);
  const unvote = useUnvoteTopic(slug);
  const [voteAnim, setVoteAnim] = useState(false);
  const [countAnim, setCountAnim] = useState(false);
  const [expanded, setExpanded] = useState(false);
  const [editOpen, setEditOpen] = useState(false);
  const [deleteOpen, setDeleteOpen] = useState(false);

  function handleVoteToggle() {
    if (topic.hasVoted) {
      unvote.mutate(topic.id);
    } else {
      vote.mutate(topic.id);
    }
    triggerAnim();
  }

  function triggerAnim() {
    setVoteAnim(false);
    setCountAnim(false);
    requestAnimationFrame(() => {
      setVoteAnim(true);
      setCountAnim(true);
      setTimeout(() => {
        setVoteAnim(false);
        setCountAnim(false);
      }, 380);
    });
  }

  // Pending state is read from the global mutation cache, scoped to THIS
  // topic, rather than from the per-row mutation observer. The backlog reorders
  // when a vote count changes, which tears down and re-subscribes this row's
  // observer mid-mutation and strands its local isPending at `true` forever.
  // The cache settles correctly regardless of remounts, so the button never
  // gets stuck disabled.
  const isPending =
    useIsMutating({
      predicate: (m) => {
        const key = m.options.mutationKey;
        const isVoteMutation =
          Array.isArray(key) &&
          key[0] === topicMutationKeys.vote[0] &&
          key[1] === topicMutationKeys.vote[1] &&
          (key[2] === topicMutationKeys.vote[2] ||
            key[2] === topicMutationKeys.unvote[2]);
        return isVoteMutation && m.state.variables === topic.id;
      },
    }) > 0;
  const hasDescription = topic.description.trim().length > 0;
  const longDescription = topic.description.length > 120;

  return (
    <div className="relative">
      <article className="bg-surface border-border group flex rounded-[var(--radius-md)] border shadow-[var(--shadow-sm)] transition-shadow hover:shadow-[var(--shadow-md)]">
        <VoteZone
          topic={topic}
          isPending={isPending}
          voteAnim={voteAnim}
          countAnim={countAnim}
          onToggle={handleVoteToggle}
        />

        {/* ── Body ── */}
        <div className="flex min-w-0 flex-1 flex-col px-4 py-3">
          <div className="flex items-start justify-between gap-2">
            <span className="text-foreground leading-snug font-semibold">
              {topic.title}
            </span>
            <HoverActions
              topic={topic}
              onEdit={() => setEditOpen(true)}
              onDelete={() => setDeleteOpen(true)}
            />
          </div>

          {/* Meta */}
          <div className="mt-0.5 flex items-center gap-1.5">
            <TopicKindBadge kind={topic.kind} />
            <span className="text-muted-foreground text-xs">
              {topic.proposerDisplayName}
            </span>
          </div>

          {/* Description */}
          {hasDescription ? (
            <>
              <div
                className={cn(
                  "text-muted-foreground mt-2 overflow-hidden text-sm leading-relaxed transition-all duration-300",
                  expanded ? "max-h-96" : "max-h-[2.75rem]",
                )}
              >
                {topic.description}
              </div>
              {longDescription && (
                <button
                  className="text-muted-foreground hover:text-foreground mt-1 flex cursor-pointer items-center gap-0.5 text-xs transition-colors"
                  onClick={() => setExpanded((v) => !v)}
                >
                  <ChevronDown
                    className={cn(
                      "h-3 w-3 transition-transform duration-200",
                      expanded && "rotate-180",
                    )}
                  />
                  {expanded ? "Show less" : "Show more"}
                </button>
              )}
            </>
          ) : (
            <p className="text-muted-foreground/50 mt-1 text-xs italic">
              No description provided.
            </p>
          )}

          {actions && <div className="mt-2">{actions}</div>}
        </div>
      </article>

      {topic.canEdit && (
        <EditTopicDialog
          slug={slug}
          topic={topic}
          isOpen={editOpen}
          onOpenChange={setEditOpen}
        />
      )}
      {topic.canDelete && (
        <DeleteTopicPopover
          slug={slug}
          topic={topic}
          isOpen={deleteOpen}
          onOpenChange={setDeleteOpen}
        />
      )}
    </div>
  );
}
