import { ArrowRight } from "lucide-react";
import { Link } from "react-router-dom";

import { SettingsCard } from "@/components/patterns/SettingsCard";
import { useSelfView } from "@/features/expertise/api/use-self-view";
import type { SelfArea, SelfView } from "@/features/expertise/domain/expertise";
import { TIER_META } from "@/features/expertise/ui/tier-meta";
import { cn } from "@/lib/cn";

const MAX_CHIPS = 12;

/** Opting out and hiding a tag are the expertise context's own preferences; this card reads
 *  them rather than re-deciding what a member has agreed to show. */
export function visibleAreas(view: SelfView): SelfArea[] {
  if (view.optedOut) return [];
  return view.areas.filter((area) => !area.hidden);
}

function ExpertiseChip({ area }: Readonly<{ area: SelfArea }>) {
  const meta = TIER_META[area.tier];
  return (
    <span
      title={meta.label}
      className="border-border bg-surface-sunken text-foreground inline-flex items-center gap-1.5 rounded-full border px-2.5 py-1 text-xs font-medium"
    >
      <span
        aria-hidden
        className={cn("size-1.5 rounded-full", meta.dotClassName)}
      />
      {area.canonicalTag}
    </span>
  );
}

/** What a member has demonstrated: a flat wrap of tag chips, each carrying its tier as a
 *  coloured dot. Caps the list at twelve chips; the "All areas" link is the overflow, not
 *  a truncation count. */
export function AccountExpertise() {
  const { data } = useSelfView();
  if (!data) return null;

  const areas = visibleAreas(data).slice(0, MAX_CHIPS);
  if (areas.length === 0) return null;

  return (
    <SettingsCard
      title="Expertise"
      action={
        <Link
          to="/me/expertise"
          className="text-primary inline-flex items-center gap-1 text-sm font-medium hover:underline"
        >
          All areas
          <ArrowRight size={13} />
        </Link>
      }
    >
      <div className="flex flex-wrap gap-2 px-4 py-3">
        {areas.map((area) => (
          <ExpertiseChip key={area.canonicalTag} area={area} />
        ))}
      </div>
    </SettingsCard>
  );
}
