import {
  closestCorners,
  DndContext,
  type DragEndEvent,
  DragOverlay,
  type DragStartEvent,
  KeyboardSensor,
  PointerSensor,
  useDndContext,
  useDroppable,
  useSensor,
  useSensors,
} from "@dnd-kit/core";
import {
  SortableContext,
  sortableKeyboardCoordinates,
  verticalListSortingStrategy,
} from "@dnd-kit/sortable";
import { useCallback, useEffect, useState } from "react";

import { useJournalWrite } from "@/features/mentoring/api/use-mentoring";
import {
  BOARD_ORDER,
  boardColumn,
  type Journal,
  type Topic,
  type TopicState,
} from "@/features/mentoring/domain/mentoring";
import type { TopicPlacement } from "@/features/mentoring/domain/mentoring-gateway";
import { AddTopic } from "@/features/mentoring/ui/board/AddTopic";
import {
  announcements,
  dropPlacement,
  type DropTarget,
  lastIn,
} from "@/features/mentoring/ui/board/board-moves";
import {
  STATE_LABELS,
  Sticker,
  StickerGhost,
  stickerId,
} from "@/features/mentoring/ui/board/Sticker";
import { TopicDialog } from "@/features/mentoring/ui/board/TopicDialog";
import { notesNaming } from "@/features/mentoring/ui/journal-people";
import { onFailure } from "@/features/mentoring/ui/notify-failure";
import { useFollowFocus } from "@/features/mentoring/ui/use-follow-focus";
import { cn } from "@/lib/cn";
import { useMediaQuery } from "@/lib/use-media-query";

/** How long a topic picked on the trail stays marked: the highlight's three pulses. */
const FOUND_MS = 3600;

interface Props {
  journal: Journal;
  /** A topic picked elsewhere, such as on the trail, to scroll to, highlight and open. */
  focusTopicId?: number | null;
  onFocusHandled?: () => void;
}

interface Board {
  journal: Journal;
  celebrating: number | null;
  found: number | null;
  onOpen: (topicId: number) => void;
  onSwitch: (topicId: number, placement: TopicPlacement | null) => void;
  onCelebrated: () => void;
}

function Column({
  state,
  board,
}: Readonly<{ state: TopicState; board: Board }>) {
  const { journal } = board;
  const topics = boardColumn(journal.topics, state);
  const { over } = useDndContext();
  const { setNodeRef } = useDroppable({
    id: `column:${state}`,
    data: { state, topicId: null } satisfies DropTarget,
    disabled: !journal.writable,
  });
  return (
    <section
      ref={setNodeRef}
      data-column={state}
      aria-label={STATE_LABELS[state]}
      className={cn(
        "bg-surface-sunken flex min-w-0 flex-col gap-3 rounded-2xl p-3 motion-safe:transition-colors",
        journal.writable && "sm:min-h-56",
        over?.data.current?.state === state &&
          "bg-[color-mix(in_srgb,var(--secondary)_14%,var(--surface-sunken))]",
      )}
    >
      <h3 className="font-display text-muted-foreground flex items-center justify-between px-1 text-sm font-semibold">
        {STATE_LABELS[state]}
        <span className="tabular-nums">{topics.length}</span>
      </h3>
      <SortableContext
        items={topics.map((topic) => stickerId(topic.id))}
        strategy={verticalListSortingStrategy}
      >
        <ul className="flex flex-col gap-3">
          {topics.map((topic, index) => (
            <Sticker
              key={topic.id}
              topic={topic}
              index={index}
              noteCount={notesNaming(journal, topic.id).length}
              writable={journal.writable}
              celebrate={board.celebrating === topic.id}
              found={board.found === topic.id}
              onOpen={() => board.onOpen(topic.id)}
              onPlace={(next) =>
                board.onSwitch(topic.id, lastIn(journal.topics, topic.id, next))
              }
              onCelebrated={board.onCelebrated}
            />
          ))}
        </ul>
      </SortableContext>
      {state === "TODO" && journal.writable && (
        <AddTopic pairingId={journal.pairing.id} />
      )}
    </section>
  );
}

/** A sticker sent to another column takes focus back on its switch once it stands there. */
function stickerLanding({
  topicId,
  state,
}: {
  topicId: number;
  state: TopicState;
}): HTMLElement | null {
  const sticker = document.querySelector<HTMLElement>(
    `[data-topic-id="${topicId}"]`,
  );
  if (sticker?.dataset.state !== state) return null;
  return (
    sticker.querySelector<HTMLElement>('[role="radio"][aria-checked="true"]') ??
    sticker.querySelector<HTMLElement>("[aria-haspopup]")
  );
}

/** A removed topic's sticker leaves with it, so focus lands on the board's heading. */
function removalLanding(topicId: number): HTMLElement | null {
  if (document.querySelector(`[data-topic-id="${topicId}"]`) !== null) {
    return null;
  }
  return document.getElementById("journal-topics");
}

/** Scrolls to a topic picked elsewhere and hands it back to the board to open. */
function useFocusedTopic(
  props: Readonly<Props>,
  open: (topicId: number) => void,
) {
  const { focusTopicId = null, onFocusHandled } = props;
  const reduced = useMediaQuery("(prefers-reduced-motion: reduce)");
  useEffect(() => {
    if (focusTopicId === null) return;
    document
      .querySelector(`[data-topic-id="${focusTopicId}"]`)
      ?.scrollIntoView?.({
        behavior: reduced ? "auto" : "smooth",
        block: "center",
      });
    open(focusTopicId);
    onFocusHandled?.();
  }, [focusTopicId, onFocusHandled, open, reduced]);
}

/** The pair's topics in To do, In progress and Done. An ended journal shows them and moves none. */
export function TopicBoard(props: Readonly<Props>) {
  const { journal } = props;
  const write = useJournalWrite(journal.pairing.id);
  const [openId, setOpenId] = useState<number | null>(null);
  const [found, setFound] = useState<number | null>(null);
  const [celebrating, setCelebrating] = useState<number | null>(null);
  const [dragged, setDragged] = useState<Topic | null>(null);
  const sensors = useSensors(
    useSensor(PointerSensor, { activationConstraint: { distance: 6 } }),
    useSensor(KeyboardSensor, {
      coordinateGetter: sortableKeyboardCoordinates,
    }),
  );
  const reveal = useCallback((topicId: number) => {
    setOpenId(topicId);
    setFound(topicId);
  }, []);
  useFocusedTopic(props, reveal);
  const onCelebrated = useCallback(() => setCelebrating(null), []);
  const followSticker = useFollowFocus(journal.topics, stickerLanding);
  const follow = (topicId: number, state: TopicState) =>
    followSticker({ topicId, state });
  const followRemoval = useFollowFocus(journal.topics, removalLanding);
  useEffect(() => {
    if (found === null) return;
    const done = window.setTimeout(() => setFound(null), FOUND_MS);
    return () => window.clearTimeout(done);
  }, [found]);

  function place(topicId: number, placement: TopicPlacement | null) {
    if (placement === null) return;
    const before = journal.topics.find((topic) => topic.id === topicId)?.state;
    if (placement.state === "DONE" && before !== "DONE")
      setCelebrating(topicId);
    write.placeTopic.mutate(
      { id: topicId, placement },
      {
        onError: (error) => {
          setCelebrating(null);
          if (before !== undefined) follow(topicId, before);
          onFailure(error);
        },
      },
    );
  }

  function switchTo(topicId: number, placement: TopicPlacement | null) {
    if (placement !== null) follow(topicId, placement.state);
    place(topicId, placement);
  }

  function dragStart({ active }: DragStartEvent) {
    const topicId = active.data.current?.topicId as number | undefined;
    setDragged(journal.topics.find((topic) => topic.id === topicId) ?? null);
  }

  function dragEnd({ active, over }: DragEndEvent) {
    setDragged(null);
    const target = over?.data.current as DropTarget | undefined;
    const topicId = active.data.current?.topicId as number | undefined;
    if (target === undefined || topicId === undefined) return;
    place(topicId, dropPlacement(journal.topics, topicId, target));
  }

  const board: Board = {
    journal,
    celebrating,
    found,
    onOpen: (topicId) => setOpenId(topicId),
    onSwitch: switchTo,
    onCelebrated,
  };
  return (
    <section aria-labelledby="journal-topics" className="flex flex-col gap-3">
      <h2
        id="journal-topics"
        tabIndex={-1}
        className="font-display text-foreground text-base font-semibold outline-none"
      >
        Topics
      </h2>
      <DndContext
        sensors={sensors}
        collisionDetection={closestCorners}
        onDragStart={dragStart}
        onDragEnd={dragEnd}
        onDragCancel={() => setDragged(null)}
        accessibility={{
          announcements: announcements(journal.topics),
          screenReaderInstructions: {
            draggable:
              "Press space to pick the topic up, the arrow keys to move it, space again to drop it, or escape to put it back.",
          },
        }}
      >
        <div className="grid grid-cols-1 gap-3 sm:grid-cols-3">
          {BOARD_ORDER.map((state) => (
            <Column key={state} state={state} board={board} />
          ))}
        </div>
        <DragOverlay dropAnimation={null}>
          {dragged !== null && (
            <StickerGhost
              topic={dragged}
              noteCount={notesNaming(journal, dragged.id).length}
            />
          )}
        </DragOverlay>
      </DndContext>
      <TopicDialog
        journal={journal}
        topic={journal.topics.find((topic) => topic.id === openId) ?? null}
        onClose={() => setOpenId(null)}
        onRemoved={followRemoval}
      />
    </section>
  );
}
