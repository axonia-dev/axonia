import { ChevronDown } from "lucide-react";
import { type ReactNode, useId, useRef, useState } from "react";

import { TagChips } from "@/components/ui/TagChips";
import type { Slot } from "@/features/events/domain/slot";
import { cn } from "@/lib/cn";
import { useClampedOverflow } from "@/lib/use-clamped-overflow";

interface Props {
  slot: Slot;
}

interface ToggleProps {
  expanded: boolean;
  controls: string;
  onPress: () => void;
}

/**
 * Floated, not a flex child: it holds the top-right corner of the subject row while the chips wrap
 * underneath it. As a flex child it would be shoved onto a line of its own the moment the tags
 * wrapped, spending the very row the placement was meant to save.
 */
function ExpandToggle({ expanded, controls, onPress }: Readonly<ToggleProps>) {
  return (
    <button
      type="button"
      onClick={onPress}
      aria-expanded={expanded}
      aria-controls={controls}
      className="text-primary hover:text-primary-strong float-right ml-3 flex cursor-pointer items-center gap-1 text-xs font-semibold"
    >
      {expanded ? "Show less" : "Show more"}
      <ChevronDown
        aria-hidden
        className={cn(
          "size-3.5 motion-safe:transition-transform",
          expanded && "rotate-180",
        )}
      />
    </button>
  );
}

/** What the talk is about, in its own words: a backlog topic, or tags, or neither. */
function hasSubject(slot: Slot): boolean {
  return slot.topicId !== null || slot.tags.length > 0;
}

/**
 * The subject row, and the host of the expand control. A flex item, so it establishes the
 * formatting context that contains its own float; `-mb-1` cancels the bottom margin the last line
 * of chips leaves behind.
 */
function SubjectRow({
  slot,
  children,
}: Readonly<{ slot: Slot; children: ReactNode }>) {
  return (
    <div className="-mb-1 text-xs">
      {children}
      {slot.topicId !== null && (
        <span className="text-secondary mr-2 font-medium">From backlog</span>
      )}
      <TagChips tags={slot.tags} />
    </div>
  );
}

/**
 * Third-tier content: the slot description on its own line, then a row for the optional backlog
 * marker and the talk's own topics when it was never proposed (a slot filled from the backlog
 * carries none of its own). Renders nothing of its own when the slot carries none of these - the
 * byline row above already says who is presenting, so there is no "no presenter yet" filler here.
 *
 * A description longer than its two lines can still be read: the clamp is measured, and only a
 * description genuinely cut off earns a control to open it.
 */
export function SlotMeta({ slot }: Readonly<Props>) {
  const [expanded, setExpanded] = useState(false);
  const description = useRef<HTMLParagraphElement>(null);
  const descriptionId = useId();
  const cutOff = useClampedOverflow(description, !expanded);

  // The control outlives the overflow that summoned it: an expanded description no longer
  // overflows, and it still has to be closable.
  const canExpand = expanded || cutOff;

  // Nothing to say means no element at all, not an empty one: as a flex child of the card, an
  // empty box would still draw the row gap on both of its sides.
  if (!slot.notes && !hasSubject(slot)) {
    return null;
  }
  return (
    <div className="flex flex-col gap-2">
      {/* The description is the card's own voice, not metadata about it: it carries the full
       * foreground tone and sits a step above the byline, which stays muted and small. */}
      {slot.notes && (
        <p
          ref={description}
          id={descriptionId}
          className={cn("text-foreground text-sm", !expanded && "line-clamp-2")}
        >
          {slot.notes}
        </p>
      )}
      {(hasSubject(slot) || canExpand) && (
        <SubjectRow slot={slot}>
          {canExpand && (
            <ExpandToggle
              expanded={expanded}
              controls={descriptionId}
              onPress={() => setExpanded((open) => !open)}
            />
          )}
        </SubjectRow>
      )}
    </div>
  );
}
