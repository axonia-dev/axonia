import { type CSSProperties, useState } from "react";

import { cn } from "@/lib/cn";

interface AvatarProps {
  name: string;
  hue: string;
  /** When set, the badge image is shown in place of the monogram. */
  src?: string | null;
  /** `circle` marks a person; `square` (default) marks an org/entity. */
  shape?: "square" | "circle";
  className?: string;
}

function initials(name: string): string {
  return name
    .trim()
    .split(/\s+/)
    .slice(0, 2)
    .map((word) => word.charAt(0).toUpperCase())
    .join("");
}

export function Avatar({
  name,
  hue,
  src,
  shape = "square",
  className,
}: Readonly<AvatarProps>) {
  const [brokenSrc, setBrokenSrc] = useState<string | undefined>(undefined);
  const shapeClass = shape === "circle" ? "rounded-full" : "rounded-md";

  if (src != null && src !== "" && src !== brokenSrc) {
    return (
      // Keyed by src: a changed src remounts a fresh <img>, so a late/aborted
      // error from a previous src can never latch the new (good) one.
      <img
        key={src}
        src={src}
        alt={name}
        onError={() => setBrokenSrc(src)}
        className={cn(
          "size-9 shrink-0 object-cover",
          shapeClass,
          "shadow-[0_1px_3px_rgba(33,26,38,0.18)]",
          className,
        )}
      />
    );
  }

  return (
    <span
      aria-hidden="true"
      style={{ "--k": hue } as CSSProperties}
      className={cn(
        "font-display inline-flex size-9 shrink-0 items-center justify-center text-sm font-bold",
        shapeClass,
        "bg-[color-mix(in_srgb,var(--k)_18%,var(--surface))] text-[color-mix(in_srgb,var(--k),var(--foreground)_var(--monogram-ink))]",
        className,
      )}
    >
      {initials(name)}
    </span>
  );
}
