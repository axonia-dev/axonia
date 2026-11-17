import { Flag, Sparkle } from "lucide-react";
import type { CSSProperties } from "react";
import { Button as AriaButton } from "react-aria-components";

import { AreaTag } from "@/components/ui/AreaTag";
import type {
  Journal,
  Note,
  Topic,
} from "@/features/mentoring/domain/mentoring";
import {
  authorRole,
  inMeetingOrder,
  notesNaming,
  plural,
  ROLE_INK,
} from "@/features/mentoring/ui/journal-people";
import type {
  Point,
  TrailLayout,
} from "@/features/mentoring/ui/trail/trail-layout";
import { cn } from "@/lib/cn";
import { calendarDay } from "@/lib/declared-on";

const STOP_META: Record<Topic["state"], string> = {
  DONE: "Done",
  IN_PROGRESS: "In progress",
  TODO: "To do",
};

/** Where a point of the layout sits in the drawn box, which keeps the layout's proportions. */
export function at(point: Point, layout: TrailLayout): CSSProperties {
  return {
    left: `${(point.x / layout.width) * 100}%`,
    top: `${(point.y / layout.height) * 100}%`,
  };
}
function Pin({ topic, still }: Readonly<{ topic: Topic; still: boolean }>) {
  const base =
    "grid size-11 place-items-center rounded-full motion-safe:transition-transform motion-safe:duration-300 motion-safe:ease-[cubic-bezier(.3,1.6,.5,1)] group-hover/stop:scale-110 group-hover/stop:-rotate-6";
  if (topic.state === "DONE") {
    return (
      <span
        className={cn(
          base,
          "bg-success text-success-foreground shadow-[0_6px_18px_color-mix(in_srgb,var(--success)_40%,transparent)]",
        )}
      >
        <Flag aria-hidden className="size-5" />
      </span>
    );
  }
  if (topic.state === "IN_PROGRESS") {
    return (
      <span
        className={cn(
          base,
          "bg-warning text-warning-foreground shadow-[0_0_0_6px_color-mix(in_srgb,var(--warning)_22%,transparent)]",
          !still &&
            "motion-safe:animate-[stop-pulse_2.2s_ease-in-out_infinite]",
        )}
      >
        <Sparkle aria-hidden className="size-5" />
      </span>
    );
  }
  return (
    <span
      className={cn(
        base,
        "border-border-strong bg-surface border-2 border-dashed",
      )}
    >
      <span className="bg-border-strong size-1.5 rounded-full" />
    </span>
  );
}

function StopCard({
  id,
  hidden,
  topic,
  notes,
  journal,
  placement,
}: Readonly<{
  id: string;
  hidden: boolean;
  topic: Topic;
  notes: Note[];
  journal: Journal;
  placement: string;
}>) {
  const latest = inMeetingOrder(notes).slice(-3);
  return (
    <div
      id={id}
      role="tooltip"
      hidden={hidden}
      className={cn(
        "border-border-strong bg-surface absolute z-20 w-64 flex-col gap-2 rounded-2xl border p-3.5 text-left shadow-lg motion-safe:animate-[hovercard-in_160ms_ease-out]",
        placement,
        !hidden && "flex",
      )}
    >
      <p className="font-display text-foreground text-sm font-semibold wrap-anywhere">
        {topic.label}
      </p>
      {topic.area !== null && <AreaTag area={topic.area} />}
      {latest.length === 0 && (
        <p className="text-muted-foreground text-xs">No notes yet.</p>
      )}
      {latest.map((note) => (
        <p key={note.id} className="flex gap-2 text-xs">
          <span
            aria-hidden
            className="mt-1 size-2 flex-none rounded-full"
            style={{
              background: ROLE_INK[authorRole(note, journal.pairing.myRole)],
            }}
          />
          <span className="text-foreground line-clamp-3 wrap-anywhere">
            {note.body}{" "}
            <span className="text-muted-foreground">
              {calendarDay(note.meetingOn)}
            </span>
          </span>
        </p>
      ))}
    </div>
  );
}

function cardPlacement(point: Point, layout: TrailLayout): string {
  const vertical =
    point.y / layout.height > 0.5 ? "bottom-full mb-2" : "top-full mt-2";
  const across = point.x / layout.width;
  if (across > 0.6) return cn(vertical, "right-0");
  if (across < 0.4) return cn(vertical, "left-0");
  return cn(vertical, "left-1/2 -translate-x-1/2");
}

export interface StopProps {
  topic: Topic;
  point: Point;
  layout: TrailLayout;
  journal: Journal;
  still: boolean;
  peeked: boolean;
  onPeek: (peek: boolean) => void;
  onOpen: () => void;
}

/** One topic on the trail: hovering or focusing it shows its notes, pressing it opens the topic. */
export function TrailStop({
  topic,
  point,
  layout,
  journal,
  still,
  peeked,
  onPeek,
  onOpen,
}: Readonly<StopProps>) {
  const notes = notesNaming(journal, topic.id);
  const cardId = `trail-stop-${topic.id}`;
  const meta =
    notes.length > 0
      ? `${STOP_META[topic.state]} · ${plural(notes.length, "note", "notes")}`
      : STOP_META[topic.state];
  return (
    <li
      style={at(point, layout)}
      className="absolute flex w-32 -translate-x-1/2 -translate-y-[22px] flex-col items-center"
    >
      <AriaButton
        onPress={onOpen}
        onHoverChange={onPeek}
        onFocusChange={onPeek}
        aria-describedby={cardId}
        onKeyDown={(event) => {
          if (event.key === "Escape" && peeked) onPeek(false);
        }}
        className="group/stop focus-visible:ring-focus-ring flex cursor-pointer flex-col items-center gap-1 rounded-xl text-center outline-none focus-visible:ring-2"
      >
        <Pin topic={topic} still={still} />
        <span className="bg-surface/85 flex flex-col items-center gap-0.5 rounded-md px-1 py-0.5">
          <span className="text-foreground text-xs leading-tight font-semibold wrap-anywhere">
            {topic.label}
          </span>
          <span className="text-muted-foreground text-[11px] leading-tight whitespace-nowrap">
            {meta}
          </span>
        </span>
      </AriaButton>
      <StopCard
        id={cardId}
        hidden={!peeked}
        topic={topic}
        notes={notes}
        journal={journal}
        placement={cardPlacement(point, layout)}
      />
    </li>
  );
}
