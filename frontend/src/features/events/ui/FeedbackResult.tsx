import type { LucideIcon } from "lucide-react";
import { Lightbulb, Smile } from "lucide-react";

import { AverageBars } from "@/components/patterns/average-bars/AverageBars";
import { formatAverage } from "@/components/patterns/average-bars/format-average";
import type { FeedbackResult as Result } from "@/features/events/domain/session-feedback";
import { plural } from "@/lib/plural";

function Average({
  icon: Icon,
  label,
  value,
}: Readonly<{ icon: LucideIcon; label: string; value: number | null }>) {
  return (
    <div className="grid grid-cols-[7.5rem_minmax(0,1fr)_auto] items-center gap-3 text-[13px] max-sm:grid-cols-[6rem_minmax(0,1fr)_auto]">
      <span className="text-foreground flex items-center gap-[7px] font-semibold">
        <Icon
          size={16}
          strokeWidth={2}
          aria-hidden="true"
          className="text-data-1"
        />
        {label}
      </span>
      <AverageBars value={value} label={label} />
      <strong
        aria-hidden="true"
        className="font-display text-foreground text-right text-[15px] tabular-nums"
      >
        {value === null ? "" : formatAverage(value)}
      </strong>
    </div>
  );
}

/** How the session landed, for the people who run it: averages only, never one answer. */
export function FeedbackResult({ result }: Readonly<{ result: Result }>) {
  if (result.state === "PENDING") return null;
  return (
    <div className="bg-surface border-border rounded-md border px-4 py-3.5">
      <h4 className="text-foreground mb-3 flex justify-between gap-3 text-[13px] font-semibold">
        How it landed
        {result.state === "REVEALED" && (
          <span className="text-muted-foreground font-normal">
            {plural(result.answers, "answer")}
          </span>
        )}
      </h4>
      {result.state === "TOO_FEW" ? (
        <p className="text-muted-foreground text-[13px]">
          Too few answers to show
        </p>
      ) : (
        <div className="flex flex-col gap-3">
          <Average icon={Smile} label="Enjoyed" value={result.enjoyed} />
          <Average icon={Lightbulb} label="Learned" value={result.learned} />
        </div>
      )}
      {result.importedFrom && (
        <p className="text-muted-foreground mt-3 text-xs">
          Imported from {result.importedFrom}
        </p>
      )}
    </div>
  );
}
