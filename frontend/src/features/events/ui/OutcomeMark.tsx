import { Check, X } from "lucide-react";

import type { ActionItemOutcome } from "@/features/events/domain/action-item";
import { cn } from "@/lib/cn";

const boxClass =
  "flex size-4 shrink-0 items-center justify-center rounded-[5px] border-2 transition-colors";

interface Props {
  outcome: ActionItemOutcome;
  /** Dims the mark where it previews an outcome rather than reporting one, so a full-colour
   *  tick never sits beside a label nobody has chosen. */
  muted?: boolean;
  className?: string;
}

/**
 * The three outcomes as three distinct boxes: an open one waits, a done one earns a tick, and one
 * the community will not do is crossed out rather than ticked - the record says which of the two
 * happened rather than collapsing both into the same mark.
 *
 * Shared by the row that reports an outcome and the field that picks one, so the list and the
 * edit dialog cannot draw the same three states differently. Decoration throughout: every caller
 * states the outcome in text of its own.
 */
export function OutcomeMark({ outcome, muted, className }: Readonly<Props>) {
  if (outcome === "DONE") {
    return (
      <span
        aria-hidden="true"
        className={cn(
          boxClass,
          muted
            ? "border-border-strong text-muted-foreground"
            : "bg-success border-success text-success-foreground",
          className,
        )}
      >
        <Check className="size-3" strokeWidth={3} />
      </span>
    );
  }
  if (outcome === "DROPPED") {
    return (
      <span
        aria-hidden="true"
        className={cn(
          boxClass,
          "text-muted-foreground border-border-strong",
          !muted && "bg-muted",
          className,
        )}
      >
        <X className="size-3" strokeWidth={3} />
      </span>
    );
  }
  return (
    <span
      aria-hidden="true"
      className={cn(boxClass, "border-border-strong", className)}
    />
  );
}
