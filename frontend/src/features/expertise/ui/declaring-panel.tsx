import type { ReactNode } from "react";

import type { Tier } from "@/features/expertise/domain/expertise";
import { DeclarationPreview } from "@/features/expertise/ui/DeclarationPreview";
import { TakingQuestionsSwitch } from "@/features/expertise/ui/TakingQuestionsSwitch";
import { cn } from "@/lib/cn";

export interface LanePreview {
  tags: string[];
  tier: Tier | null;
  paused: boolean;
}

/** The add field and the switch on the left, the card others see on the right; stacked on a phone. */
export function declaringPanel(
  field: ReactNode,
  preview: LanePreview,
  inCommunity: boolean,
) {
  const showsCard = preview.tags.length > 0;
  const offersPause = showsCard || preview.paused;
  if (field === null && !offersPause) return null;
  return (
    <div
      className={
        showsCard
          ? "mt-2 grid grid-cols-1 gap-4 sm:grid-cols-2 sm:items-start"
          : "mt-2 grid grid-cols-1 gap-4"
      }
    >
      <div className="flex min-w-0 flex-col gap-3">
        {field}
        {offersPause && (
          <div className={cn(field !== null && "border-border border-t pt-3")}>
            <TakingQuestionsSwitch
              paused={preview.paused}
              inCommunity={inCommunity}
            />
          </div>
        )}
      </div>
      {showsCard && (
        <DeclarationPreview
          tags={preview.tags}
          tier={preview.tier}
          paused={preview.paused}
        />
      )}
    </div>
  );
}
