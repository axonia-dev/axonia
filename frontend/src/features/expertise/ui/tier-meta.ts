import type { Tier } from "@/features/expertise/domain/expertise";

interface TierMeta {
  label: string;
  /** Chip background + text classes (used by TierBadge). */
  badgeClassName: string;
  /** Solid dot / accent-rail background class. */
  dotClassName: string;
  /** The colour dotClassName paints, as a CSS value for SVG; Open to questions' is its dashed primary. */
  colour: string;
}

/** Strongest first, the order tiers are grouped and displayed in. A declaration is not evidence, so it is last. */
export const TIER_ORDER: readonly Tier[] = [
  "PRACTITIONER",
  "CONTRIBUTOR",
  "INTERESTED",
  "OPEN_TO_QUESTIONS",
];

export const TIER_META: Record<Tier, TierMeta> = {
  PRACTITIONER: {
    label: "Practitioner",
    badgeClassName:
      "bg-[color-mix(in_srgb,var(--success)_16%,var(--surface))] text-[color-mix(in_srgb,var(--success)_72%,var(--foreground))]",
    dotClassName: "bg-[var(--success)]",
    colour: "var(--success)",
  },
  CONTRIBUTOR: {
    label: "Contributor",
    badgeClassName:
      "bg-[color-mix(in_srgb,var(--secondary)_16%,var(--surface))] text-[color-mix(in_srgb,var(--secondary)_68%,var(--foreground))]",
    dotClassName: "bg-[var(--secondary)]",
    colour: "var(--secondary)",
  },
  INTERESTED: {
    label: "Interested",
    badgeClassName: "bg-surface-sunken text-muted-foreground",
    dotClassName:
      "bg-[color-mix(in_srgb,var(--muted-foreground)_45%,var(--surface))]",
    colour: "color-mix(in srgb,var(--muted-foreground) 45%,var(--surface))",
  },
  OPEN_TO_QUESTIONS: {
    label: "Open to questions",
    badgeClassName:
      "border border-dashed border-[color-mix(in_srgb,var(--primary)_85%,var(--foreground))] px-[7px] py-px text-[color-mix(in_srgb,var(--primary)_60%,var(--foreground))]",
    dotClassName:
      "bg-[repeating-linear-gradient(135deg,color-mix(in_srgb,var(--primary)_85%,var(--foreground))_0_2px,transparent_2px_4px)]",
    colour: "color-mix(in srgb,var(--primary) 85%,var(--foreground))",
  },
};
