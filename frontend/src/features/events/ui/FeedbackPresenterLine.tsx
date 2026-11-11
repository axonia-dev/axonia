import { PenLine } from "lucide-react";

import { closesOn } from "@/features/events/ui/feedback-closes-on";

/** What a speaker sees in place of the ask: their own session is not theirs to rate. */
export function FeedbackPresenterLine({
  closesAt,
}: Readonly<{ closesAt: string | null }>) {
  return (
    <p className="bg-surface border-border-strong text-muted-foreground flex items-center gap-2.5 rounded-md border border-dashed px-3.5 py-3 text-[13px]">
      <PenLine
        size={18}
        strokeWidth={2}
        aria-hidden="true"
        className="text-data-1 flex-none"
      />
      {closesAt
        ? `Attendees are sharing how it went until ${closesOn(closesAt)}.`
        : "Attendees are sharing how it went."}
    </p>
  );
}
