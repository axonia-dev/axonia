import type { IconName } from "lucide-react/dynamic";
import { DynamicIcon } from "lucide-react/dynamic";
import type { CSSProperties } from "react";

import { cn } from "@/lib/cn";
import { kindHue } from "@/lib/kind-hue";

// Lucide glyph per broad section family, matched on a keyword in the
// (tenant-managed) type label. Used only when a type has no stored icon.
const INFERENCE: ReadonlyArray<[readonly string[], string]> = [
  [["keynote", "talk", "present", "speaker"], "presentation"],
  [["workshop", "hands-on", "lab"], "wrench"],
  [["demo", "showcase"], "monitor-play"],
  [["retrospective", "retro", "review"], "refresh-cw"],
  [["icebreaker", "warm"], "flame"],
  [["lightning", "quick", "short"], "zap"],
  [["panel", "discussion", "round"], "message-square"],
  [["break", "pause", "rest"], "coffee"],
];

const DEFAULT_ICON = "layout-list";

function inferIcon(label: string): string {
  const lower = label.toLowerCase();
  for (const [keywords, name] of INFERENCE) {
    if (keywords.some((keyword) => lower.includes(keyword))) {
      return name;
    }
  }
  return DEFAULT_ICON;
}

/** Deterministic accent hue for a section type, shared with the chip row. */
function sectionTypeAccent(typeId: number): string {
  return kindHue(String(typeId));
}

interface SectionTypeIconProps {
  typeId: number;
  label: string;
  icon?: string | null | undefined;
  accentColor?: string | null | undefined;
  size?: number;
  className?: string;
}

/** A section type's leading glyph on its accent tint. */
export function SectionTypeIcon({
  typeId,
  label,
  icon,
  accentColor,
  size = 18,
  className,
}: Readonly<SectionTypeIconProps>) {
  const name = icon ?? inferIcon(label);
  const accent = accentColor ?? sectionTypeAccent(typeId);
  return (
    <span
      aria-hidden
      style={{ "--k": accent } as CSSProperties}
      className={cn(
        "inline-flex shrink-0 items-center justify-center rounded-md p-1.5",
        "bg-[color-mix(in_srgb,var(--k)_16%,var(--surface))] text-[color-mix(in_srgb,var(--k)_80%,var(--foreground))]",
        className,
      )}
    >
      <DynamicIcon name={name as IconName} size={size} strokeWidth={1.8} />
    </span>
  );
}
