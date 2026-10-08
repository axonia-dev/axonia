import type { LucideIcon } from "lucide-react";
import { FileText, Presentation } from "lucide-react";

import type { ContributionType } from "@/features/recognition/domain/recognition";

interface ContributionMeta {
  /** Singular noun for a single contribution of this type. */
  noun: string;
  /** Plural noun for counts. */
  nounPlural: string;
  icon: LucideIcon;
  /** Solid accent-dot / bar background class. */
  dotClassName: string;
  /** Translucent tint for the icon chip background in ContributionCard. */
  chipClassName: string;
  /** Community sub-page a contribution of this type links to. */
  sourcePath: string;
}

/** Talks first — the order types are grouped and displayed in. */
export const CONTRIBUTION_ORDER: readonly ContributionType[] = [
  "SLOT_PRESENTED",
  "ARTIFACT_CAPTURED",
];

export const CONTRIBUTION_META: Record<ContributionType, ContributionMeta> = {
  SLOT_PRESENTED: {
    noun: "talk",
    nounPlural: "talks",
    icon: Presentation,
    dotClassName: "bg-[var(--primary)]",
    chipClassName: "bg-[color-mix(in_srgb,var(--primary)_18%,var(--surface))]",
    sourcePath: "schedule",
  },
  ARTIFACT_CAPTURED: {
    noun: "artifact",
    nounPlural: "artifacts",
    icon: FileText,
    dotClassName: "bg-[var(--secondary)]",
    chipClassName:
      "bg-[color-mix(in_srgb,var(--secondary)_18%,var(--surface))]",
    sourcePath: "library",
  },
};
