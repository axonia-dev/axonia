import type { Tier } from "@/features/expertise/domain/expertise";
import { TIER_META } from "@/features/expertise/ui/tier-meta";
import { cn } from "@/lib/cn";

interface Props {
  tier: Tier;
}

export function TierBadge({ tier }: Readonly<Props>) {
  const meta = TIER_META[tier];
  return (
    <span
      className={cn(
        "inline-flex items-center rounded-full px-2 py-0.5 text-xs font-medium",
        meta.badgeClassName,
      )}
    >
      {meta.label}
    </span>
  );
}
