import { useState } from "react";

import type { Journal } from "@/features/mentoring/domain/mentoring";
import {
  authorRole,
  inMeetingOrder,
  ROLE_INK,
} from "@/features/mentoring/ui/journal-people";
import {
  footprints,
  MAX_STOPS,
  pathOf,
  standingIndex,
  type TrailLayout,
  trailLayout,
  trailOrder,
} from "@/features/mentoring/ui/trail/trail-layout";
import { at, TrailStop } from "@/features/mentoring/ui/trail/TrailStop";
import { Walkers } from "@/features/mentoring/ui/trail/Walkers";
import { cn } from "@/lib/cn";
import { useMediaQuery } from "@/lib/use-media-query";

interface Props {
  journal: Journal;
  onOpenTopic: (topicId: number) => void;
}

function Ground({
  journal,
  layout,
  standing,
}: Readonly<{ journal: Journal; layout: TrailLayout; standing: number }>) {
  const walked = layout.legs.slice(0, standing + 1);
  const notes = inMeetingOrder(journal.notes);
  const prints = footprints(walked, notes.length);
  return (
    <svg
      aria-hidden
      data-testid="trail-ground"
      viewBox={`0 0 ${layout.width} ${layout.height}`}
      className="absolute inset-0 size-full overflow-visible"
    >
      <path
        d={layout.path}
        fill="none"
        stroke="var(--border-strong)"
        strokeWidth={22}
        strokeLinecap="round"
      />
      <path
        d={pathOf(layout.start, walked)}
        fill="none"
        stroke="color-mix(in srgb, var(--success) 35%, transparent)"
        strokeWidth={22}
        strokeLinecap="round"
      />
      <g data-testid="footprints">
        {prints.map((print, index) => (
          <ellipse
            key={notes[index]!.id}
            cx={print.x}
            cy={print.y}
            rx={5}
            ry={3.5}
            fill={ROLE_INK[authorRole(notes[index]!, journal.pairing.myRole)]}
          />
        ))}
      </g>
    </svg>
  );
}

function peekAfter(
  current: number | null,
  topicId: number,
  on: boolean,
): number | null {
  if (on) return topicId;
  return current === topicId ? null : current;
}

/** The pairing as a walk: its topics are stops along a winding path, its notes the footprints that lead to where the pair stands. */
export function JournalTrail({ journal, onOpenTopic }: Readonly<Props>) {
  const phone = useMediaQuery("(max-width: 639px)");
  const still = useMediaQuery("(prefers-reduced-motion: reduce)");
  const [peek, setPeek] = useState<number | null>(null);
  const ordered = trailOrder(journal.topics).slice(0, MAX_STOPS);
  if (ordered.length === 0) {
    return (
      <p className="text-muted-foreground text-sm">
        No stops yet. Each topic added to the board becomes a stop on this
        trail.
      </p>
    );
  }
  const orientation = phone ? "vertical" : "horizontal";
  const layout = trailLayout(ordered.length, orientation);
  const standing = standingIndex(journal.topics);
  return (
    <div
      data-orientation={orientation}
      className={cn("relative w-full", phone && "mx-auto max-w-sm")}
      style={{ aspectRatio: `${layout.width} / ${layout.height}` }}
    >
      <Ground journal={journal} layout={layout} standing={standing} />
      <span
        aria-hidden
        style={at(layout.stops[standing]!, layout)}
        className="absolute z-10 -translate-x-1/2 -translate-y-[62px]"
      >
        <Walkers pairing={journal.pairing} still={still} />
      </span>
      <ol aria-label="Trail">
        {ordered.map((topic, index) => (
          <TrailStop
            key={topic.id}
            topic={topic}
            point={layout.stops[index]!}
            layout={layout}
            journal={journal}
            still={still}
            peeked={peek === topic.id}
            onPeek={(on) =>
              setPeek((current) => peekAfter(current, topic.id, on))
            }
            onOpen={() => onOpenTopic(topic.id)}
          />
        ))}
      </ol>
    </div>
  );
}
