import type { ReactNode } from "react";

import type { FocalPoint } from "@/features/community/domain/community";
import {
  BANNER_SCRIM,
  focalPosition,
} from "@/features/community/ui/community-accent";
import { cn } from "@/lib/cn";

interface HeroBannerProps {
  heroUrl: string | null | undefined;
  focus: FocalPoint | null | undefined;
  editing: boolean;
  overlay?: ReactNode;
}

/**
 * A ratio rather than a fixed height: every photograph anyone uploads is roughly 4:3 or 3:2, so
 * the frame's proportion decides how much of the Organizer's image survives at all. A ratio also
 * means the card looks the same wherever it is rendered, at any width.
 *
 * The banner is a real `<img>` rather than a CSS background: the drag-to-reposition control needs
 * an `HTMLImageElement` for its `naturalWidth`/`naturalHeight` and as the target of its live drag
 * preview, neither of which a background-image can offer.
 */
export function HeroBanner({
  heroUrl,
  focus,
  editing,
  overlay,
}: Readonly<HeroBannerProps>) {
  if (heroUrl == null) {
    return (
      <div
        data-ceremony-part="banner"
        className={cn(
          "from-primary/10 to-secondary/10 relative aspect-3/2 bg-linear-to-br sm:aspect-32/9",
          editing && "group/banner",
        )}
      >
        {overlay}
      </div>
    );
  }

  return (
    <div
      data-focal-frame
      data-ceremony-part="banner"
      className={cn(
        "relative aspect-3/2 overflow-hidden sm:aspect-32/9",
        editing && "group/banner",
      )}
    >
      <img
        data-focal-image
        src={heroUrl}
        alt=""
        className="absolute inset-0 size-full object-cover"
        style={{ objectPosition: focalPosition(focus) }}
      />
      {/* A real element rather than an ::after: a generated pseudo-element paints over its own
          children and would hide anything the overlay draws. `overlay` is rendered after it
          deliberately. */}
      <div aria-hidden className="absolute inset-0" style={BANNER_SCRIM} />
      {overlay}
    </div>
  );
}
