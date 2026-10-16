import { FileText, Heart, Video } from "lucide-react";
import type { CSSProperties, ReactNode } from "react";

import {
  MorphPart,
  sessionMorphId,
} from "@/components/patterns/morph/MorphPart";
import type { Memory } from "@/features/events/domain/memory";
import { isAwaitingAnswer } from "@/features/events/domain/memory";
import {
  pageTitleOf,
  slotsOf,
  timeLabel,
} from "@/features/events/domain/schedule";
import { isOpen } from "@/features/events/domain/slot";
import { SessionMorphLink } from "@/features/events/ui/SessionMorphLink";
import { accessibleName } from "@/features/events/ui/timeline/timeline";

interface Props {
  memory: Memory;
  label: string;
  href: string;
  canManage: boolean;
  fade: number;
}

function counted(count: number, noun: string): string {
  return `${count} ${noun}${count === 1 ? "" : "s"}`;
}

/** Seen, not heard: the link's name speaks every trace, so the list stays out of the way. */
function Trace({ children }: Readonly<{ children: ReactNode }>) {
  return (
    <li aria-hidden="true" className="inline-flex items-center gap-1">
      {children}
    </li>
  );
}

function Traces({ traces }: Readonly<{ traces: Memory["traces"] }>) {
  const { loves, recorded, artifacts } = traces;
  return (
    <ul className="text-ink-sepia mt-2 flex flex-wrap items-center gap-x-3 gap-y-1 text-xs">
      {loves > 0 && (
        <Trace>
          <Heart size={13} aria-hidden="true" />
          <span aria-hidden="true">{loves}</span>
        </Trace>
      )}
      {recorded && (
        <Trace>
          <Video size={13} aria-hidden="true" />
          <span aria-hidden="true">Recording</span>
        </Trace>
      )}
      {artifacts > 0 && (
        <Trace>
          <FileText size={13} aria-hidden="true" />
          <span aria-hidden="true">{counted(artifacts, "artifact")}</span>
        </Trace>
      )}
    </ul>
  );
}

function spokenTraces({ loves, recorded, artifacts }: Memory["traces"]) {
  return [
    loves > 0 ? counted(loves, "love") : null,
    recorded ? "recording" : null,
    artifacts > 0 ? counted(artifacts, "artifact") : null,
  ].filter((part) => part !== null);
}

/** The timeline card's name, then what a sighted reader sees under it: traces or the open answer. */
function memoryName(memory: Memory, label: string, canManage: boolean): string {
  const name = accessibleName(memory.entry, label);
  if (!isAwaitingAnswer(memory.entry)) {
    return [name, ...spokenTraces(memory.traces)].join(", ");
  }
  return canManage ? `${name}, awaiting an answer` : name;
}

/** Age fades the paper toward the page, never the ink, so the text keeps its contrast. */
function paperStyle(fade: number): CSSProperties {
  const paper = Math.round(fade * 100);
  return {
    backgroundColor: `color-mix(in srgb, var(--paper) ${paper}%, var(--background))`,
    "--memory-edge": `color-mix(in srgb, var(--paper-edge) ${paper}%, var(--background))`,
  } as CSSProperties;
}

function metaLine({ entry }: Memory): string {
  const talks = slotsOf(entry).filter((slot) => !isOpen(slot)).length;
  return [timeLabel(entry), talks > 0 ? counted(talks, "talk") : null]
    .filter((part) => part !== null)
    .join(" · ");
}

export function MemoryCard({
  memory,
  label,
  href,
  canManage,
  fade,
}: Readonly<Props>) {
  const { entry, traces } = memory;
  const { sessionId } = entry;
  const morphId = sessionId === null ? undefined : sessionMorphId(sessionId);
  const awaiting = isAwaitingAnswer(entry);
  const meta = metaLine(memory);

  return (
    <MorphPart part="surface" id={morphId}>
      <SessionMorphLink
        sessionId={sessionId}
        to={href}
        aria-label={memoryName(memory, label, canManage)}
        style={paperStyle(fade)}
        className="text-ink-sepia group-hover:border-ink-sepia-soft block rounded-md border border-[color:var(--memory-edge)] p-4 transition-[border-color] duration-200"
      >
        <MorphPart
          part="title"
          id={label === pageTitleOf(entry) ? morphId : undefined}
        >
          <span className="font-display inline-block font-medium">{label}</span>
        </MorphPart>
        {meta !== "" && <p className="mt-1 text-xs">{meta}</p>}
        {awaiting && canManage && (
          <span className="bg-surface-sunken text-muted-foreground mt-2 inline-block rounded-full px-2 py-0.5 text-xs">
            Awaiting an answer
          </span>
        )}
        {!awaiting && <Traces traces={traces} />}
      </SessionMorphLink>
    </MorphPart>
  );
}
