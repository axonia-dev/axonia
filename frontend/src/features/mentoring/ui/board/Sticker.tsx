import { useSortable } from "@dnd-kit/sortable";
import { CSS } from "@dnd-kit/utilities";
import { GripVertical } from "lucide-react";
import type { ReactNode } from "react";
import { Button as AriaButton } from "react-aria-components";

import { AreaTag } from "@/components/ui/AreaTag";
import { SegmentedControl } from "@/components/ui/SegmentedControl";
import type { Topic, TopicState } from "@/features/mentoring/domain/mentoring";
import { Confetti } from "@/features/mentoring/ui/board/Confetti";
import { plural } from "@/features/mentoring/ui/journal-people";
import { PlannedDay } from "@/features/mentoring/ui/PlannedDay";
import { cn } from "@/lib/cn";
import { useMediaQuery } from "@/lib/use-media-query";

export const STATE_LABELS: Record<TopicState, string> = {
  TODO: "To do",
  IN_PROGRESS: "In progress",
  DONE: "Done",
};

const STATE_OPTIONS = (Object.keys(STATE_LABELS) as TopicState[]).map((id) => ({
  id,
  label: STATE_LABELS[id],
}));

export function stickerId(topicId: number): string {
  return `topic:${topicId}`;
}

export interface StickerProps {
  topic: Topic;
  index: number;
  noteCount: number;
  plannedOn: string | null;
  writable: boolean;
  celebrate: boolean;
  found: boolean;
  onOpen: () => void;
  onPlace: (state: TopicState) => void;
  onCelebrated: () => void;
}

function Stamp({ fresh }: Readonly<{ fresh: boolean }>) {
  return (
    <span
      aria-hidden
      className={cn(
        "border-success-ink text-success-ink font-display absolute top-2 right-2.5 rotate-10 rounded-md border-2 px-1.5 text-[11px] leading-4 font-bold tracking-[0.12em] uppercase",
        fresh &&
          "motion-safe:animate-[stamp-in_0.5s_cubic-bezier(.3,1.6,.5,1)_both]",
      )}
    >
      Done
    </span>
  );
}

function Tape() {
  return (
    <span
      aria-hidden
      className="absolute -top-1.5 left-1/2 h-3.5 w-12 -translate-x-1/2 -rotate-3 rounded-[2px] bg-[color-mix(in_srgb,var(--warning)_45%,transparent)]"
    />
  );
}

function Counts({ notes, links }: Readonly<{ notes: number; links: number }>) {
  const parts = [
    notes > 0 ? plural(notes, "note", "notes") : null,
    links > 0 ? plural(links, "link", "links") : null,
  ].filter((part) => part !== null);
  if (parts.length === 0) return null;
  return (
    <span className="text-muted-foreground text-xs">{parts.join(" · ")}</span>
  );
}

const stickerClass =
  "relative flex flex-col gap-2 rounded-xl p-3 shadow-[0_2px_0_var(--border-strong)] motion-safe:transition-[rotate,translate,box-shadow] motion-safe:duration-200";

interface Look {
  done: boolean;
  writable: boolean;
  tilt: "left" | "right";
  dragging: boolean;
  found: boolean;
}

function lookOf({ done, writable, tilt, dragging, found }: Look): string {
  return cn(
    stickerClass,
    done
      ? "bg-[color-mix(in_srgb,var(--success)_12%,var(--surface))]"
      : "bg-surface",
    writable && "motion-safe:hover:-translate-y-0.5",
    writable && tilt === "left" && "motion-safe:hover:-rotate-1",
    writable && tilt === "right" && "motion-safe:hover:rotate-1",
    dragging && "opacity-40",
    found &&
      "motion-reduce:ring-secondary motion-safe:animate-[topic-found_1.2s_ease-in-out_3] motion-reduce:ring-2",
  );
}

type Sortable = ReturnType<typeof useSortable>;

function Grip({
  label,
  grip,
  gripRef,
}: Readonly<{
  label: string;
  grip: Sortable["attributes"];
  gripRef: Sortable["setActivatorNodeRef"];
}>) {
  return (
    <button
      type="button"
      ref={gripRef}
      {...grip}
      aria-label={`Move ${label}`}
      className="text-muted-foreground hover:text-foreground focus-visible:ring-focus-ring -ml-1.5 flex h-5 w-5 flex-none cursor-grab touch-none items-center justify-center rounded-sm outline-none focus-visible:ring-2"
    >
      <GripVertical aria-hidden className="size-4" />
    </button>
  );
}

function Face({
  topic,
  noteCount,
  plannedOn,
  grip,
  onOpen,
}: Readonly<{
  topic: Topic;
  noteCount: number;
  plannedOn: string | null;
  grip: ReactNode;
  onOpen: () => void;
}>) {
  const done = topic.state === "DONE";
  return (
    <>
      {done ? null : <Tape />}
      <div className={cn("flex items-start gap-1", done && "pr-14")}>
        {grip}
        <AriaButton
          aria-haspopup="dialog"
          onPress={onOpen}
          className="text-foreground focus-visible:ring-focus-ring min-w-0 flex-1 cursor-pointer rounded-sm text-left text-sm font-semibold wrap-anywhere outline-none hover:underline focus-visible:ring-2"
        >
          {topic.label}
        </AriaButton>
      </div>
      <div className="flex flex-wrap items-center gap-2">
        {topic.area !== null && <AreaTag area={topic.area} />}
        <Counts notes={noteCount} links={topic.pointers.length} />
        <PlannedDay day={plannedOn} />
      </div>
    </>
  );
}

/** One topic on the board: dragged by pointer anywhere, by keyboard from its grip, or moved with its state switch. */
export function Sticker(props: Readonly<StickerProps>) {
  const { topic, index, writable, celebrate } = props;
  const still = useMediaQuery("(prefers-reduced-motion: reduce)");
  const {
    attributes,
    listeners,
    setNodeRef,
    setActivatorNodeRef,
    transform,
    transition,
    isDragging,
  } = useSortable({
    id: stickerId(topic.id),
    data: { state: topic.state, topicId: topic.id },
    disabled: !writable,
    ...(still ? { transition: null } : {}),
  });
  const done = topic.state === "DONE";
  return (
    <li
      ref={setNodeRef}
      data-topic-id={topic.id}
      data-state={topic.state}
      data-position={index}
      style={{ transform: CSS.Translate.toString(transform), transition }}
      {...(writable ? listeners : {})}
      className={lookOf({
        done,
        writable,
        tilt: index % 2 === 0 ? "left" : "right",
        dragging: isDragging,
        found: props.found,
      })}
    >
      {done && <Stamp fresh={celebrate} />}
      <Face
        topic={topic}
        noteCount={props.noteCount}
        plannedOn={props.plannedOn}
        onOpen={props.onOpen}
        grip={
          writable && (
            <Grip
              label={topic.label}
              grip={attributes}
              gripRef={setActivatorNodeRef}
            />
          )
        }
      />
      {writable && (
        <SegmentedControl
          label={`State of ${topic.label}`}
          hideLabel
          size="sm"
          value={topic.state}
          options={STATE_OPTIONS}
          onChange={(state) => props.onPlace(state as TopicState)}
        />
      )}
      {celebrate && done && <Confetti onDone={props.onCelebrated} />}
    </li>
  );
}

/** The copy that follows the pointer while the sticker itself waits, faded, in its column. */
export function StickerGhost({
  topic,
  noteCount,
  plannedOn,
}: Readonly<{ topic: Topic; noteCount: number; plannedOn: string | null }>) {
  const done = topic.state === "DONE";
  return (
    <div
      aria-hidden
      inert
      data-ghost-of={topic.id}
      className={cn(
        lookOf({
          done,
          writable: false,
          tilt: "left",
          dragging: false,
          found: false,
        }),
        "cursor-grabbing shadow-lg motion-safe:rotate-2",
      )}
    >
      {done && <Stamp fresh={false} />}
      <Face
        topic={topic}
        noteCount={noteCount}
        plannedOn={plannedOn}
        grip={null}
        onOpen={() => undefined}
      />
    </div>
  );
}
