import type { IconName } from "lucide-react/dynamic";
import { DynamicIcon } from "lucide-react/dynamic";
import type { CSSProperties } from "react";

import { cn } from "@/lib/cn";
import { kindHue } from "@/lib/kind-hue";

// Lucide glyph per broad artifact family, matched on a keyword in the
// (tenant-managed) type label. Used only when a type has no stored icon.
const INFERENCE: ReadonlyArray<[readonly string[], string]> = [
  [["record", "video", "recording"], "circle-play"],
  [["talk", "slide", "deck", "present"], "presentation"],
  [["repo", "code", "repository", "source"], "git-branch"],
  [["rfc", "spec", "proposal"], "file-text"],
  [["adr", "decision"], "file-check"],
  [["article", "post", "blog", "write"], "newspaper"],
  [["note", "doc"], "notebook-pen"],
  [["link", "url", "bookmark"], "link"],
];

const DEFAULT_ICON = "file";

function inferIcon(label: string): string {
  const lower = label.toLowerCase();
  for (const [keywords, name] of INFERENCE) {
    if (keywords.some((keyword) => lower.includes(keyword))) {
      return name;
    }
  }
  return DEFAULT_ICON;
}

/** Deterministic accent hue for a type, shared with the chip row. */
export function typeAccent(typeId: number): string {
  return kindHue(String(typeId));
}

interface TypeIconProps {
  typeId: number;
  label: string;
  icon?: string | null | undefined;
  accentColor?: string | null | undefined;
  size?: number;
  className?: string;
}

/** A type's leading glyph on its accent tint — the card's visual spine. */
export function TypeIcon({
  typeId,
  label,
  icon,
  accentColor,
  size = 18,
  className,
}: Readonly<TypeIconProps>) {
  const name = icon ?? inferIcon(label);
  const accent = accentColor ?? typeAccent(typeId);
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
