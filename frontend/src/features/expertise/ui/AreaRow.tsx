import { ChevronDown } from "lucide-react";
import { type ReactNode, useState } from "react";

import { totalSignals } from "@/features/expertise/domain/area-filter";
import type {
  Evidence,
  EvidenceItem,
  SelfArea,
} from "@/features/expertise/domain/expertise";
import { AreaVisibilityButton } from "@/features/expertise/ui/AreaVisibilityButton";
import { EvidenceDetails } from "@/features/expertise/ui/EvidenceDetails";
import { SignalSparkline } from "@/features/expertise/ui/SignalSparkline";
import { TIER_META } from "@/features/expertise/ui/tier-meta";
import { cn } from "@/lib/cn";

function summaryText(evidence: Evidence): string {
  const parts: string[] = [];
  if (evidence.presentedCount > 0) {
    parts.push(
      `${evidence.presentedCount} ${evidence.presentedCount === 1 ? "talk" : "talks"}`,
    );
  }
  if (evidence.contributedCount > 0) {
    parts.push(
      `${evidence.contributedCount} ${evidence.contributedCount === 1 ? "contribution" : "contributions"}`,
    );
  }
  if (evidence.offeredCount > 0) {
    parts.push(`${evidence.offeredCount} offered`);
  }
  return parts.join(" · ");
}

interface Props {
  area: SelfArea;
  onHide: (tag: string) => void;
  onUnhide: (tag: string) => void;
  onDismiss: (item: EvidenceItem, tag: string) => void;
  marker?: ReactNode;
  slug?: string | undefined;
}

export function AreaRow({
  area,
  onHide,
  onUnhide,
  onDismiss,
  marker,
  slug,
}: Readonly<Props>) {
  const [expanded, setExpanded] = useState(false);
  const summary = summaryText(area.evidence);

  return (
    <div
      className={cn(
        "border-border bg-surface overflow-hidden rounded-xl border shadow-[0_1px_2px_rgba(94,23,64,0.05)]",
        area.hidden && "opacity-60",
      )}
    >
      <div className="flex items-center gap-3 px-4 py-3">
        <span
          aria-hidden
          className={cn(
            "h-8 w-[3px] shrink-0 rounded-full",
            TIER_META[area.tier].dotClassName,
          )}
        />
        <div className="min-w-0 flex-1">
          <p className="text-foreground truncate font-medium">
            {area.canonicalTag}
            {area.hidden && (
              <span className="text-muted-foreground ml-2 text-xs font-normal">
                · hidden from others
              </span>
            )}
          </p>
          {summary !== "" && (
            <p className="text-muted-foreground mt-0.5 text-xs">{summary}</p>
          )}
          {marker}
        </div>

        <SignalSparkline evidence={area.evidence} />

        <AreaVisibilityButton
          tag={area.canonicalTag}
          hidden={area.hidden}
          onPress={() =>
            area.hidden
              ? onUnhide(area.canonicalTag)
              : onHide(area.canonicalTag)
          }
        />

        <button
          type="button"
          aria-expanded={expanded}
          aria-label={expanded ? "Hide evidence" : "Show evidence"}
          onClick={() => setExpanded((prev) => !prev)}
          className="border-border text-muted-foreground hover:text-primary flex size-8 shrink-0 cursor-pointer items-center justify-center rounded-lg border transition-colors"
        >
          <ChevronDown
            size={16}
            className={cn("transition-transform", expanded && "rotate-180")}
          />
        </button>
      </div>

      {expanded && (
        <div className="border-border bg-surface border-t px-4 py-2 motion-safe:animate-[slot-in_180ms_ease-out]">
          <EvidenceDetails
            items={area.evidence.recent}
            tag={area.canonicalTag}
            totalCount={totalSignals(area)}
            slug={slug}
            onDismiss={(item) => onDismiss(item, area.canonicalTag)}
          />
        </div>
      )}
    </div>
  );
}
