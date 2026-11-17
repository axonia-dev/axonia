import type { Announcements, Over } from "@dnd-kit/core";

import {
  boardColumn,
  type Topic,
  type TopicState,
} from "@/features/mentoring/domain/mentoring";
import type { TopicPlacement } from "@/features/mentoring/domain/mentoring-gateway";
import { STATE_LABELS, stickerId } from "@/features/mentoring/ui/board/Sticker";

/** Where a sticker was let go: on another sticker, or on a column's free space. */
export interface DropTarget {
  state: TopicState;
  topicId: number | null;
}

/** The last slot of a column, counted without the sticker that moves into it. */
export function lastIn(
  topics: readonly Topic[],
  topicId: number,
  state: TopicState,
): TopicPlacement {
  const others = topics.filter((topic) => topic.id !== topicId);
  return { state, position: boardColumn(others, state).length };
}

/** A sticker dropped on another takes its slot; on free space it goes last. Null when nothing moves. */
export function dropPlacement(
  topics: readonly Topic[],
  topicId: number,
  target: DropTarget,
): TopicPlacement | null {
  const moving = topics.find((topic) => topic.id === topicId);
  if (moving === undefined) return null;
  const slot = boardColumn(topics, target.state).findIndex(
    (topic) => topic.id === target.topicId,
  );
  const placement =
    slot < 0
      ? lastIn(topics, topicId, target.state)
      : { state: target.state, position: slot };
  const current = boardColumn(topics, moving.state).indexOf(moving);
  const unmoved =
    placement.state === moving.state && placement.position === current;
  return unmoved ? null : placement;
}

function labelOf(topics: readonly Topic[], id: string | number): string {
  return topics.find((topic) => stickerId(topic.id) === id)?.label ?? "Topic";
}

/** What a screen reader hears while a sticker is dragged. */
export function announcements(topics: readonly Topic[]): Announcements {
  const where = (over: Over | null) =>
    STATE_LABELS[
      (over?.data.current?.state as TopicState | undefined) ?? "TODO"
    ];
  return {
    onDragStart: ({ active }) => `Picked up ${labelOf(topics, active.id)}.`,
    onDragOver: ({ active, over }) =>
      over === null
        ? undefined
        : `${labelOf(topics, active.id)} is over ${where(over)}.`,
    onDragEnd: ({ active, over }) =>
      over === null
        ? `${labelOf(topics, active.id)} was put back.`
        : `${labelOf(topics, active.id)} was dropped in ${where(over)}.`,
    onDragCancel: ({ active }) => `${labelOf(topics, active.id)} was put back.`,
  };
}
