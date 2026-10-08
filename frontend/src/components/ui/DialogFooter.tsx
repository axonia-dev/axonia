import type { ReactNode } from "react";

import { cn } from "@/lib/cn";

interface DialogFooterProps {
  /**
   * Action buttons in reading order: the dismiss/Cancel button first, the
   * primary or destructive action last. The footer right-aligns them, so the
   * primary action always lands in the bottom-right corner.
   */
  children: ReactNode;
  /**
   * Pins the row to the foot of the panel, for a form long enough that the panel scrolls.
   *
   * Only valid inside a {@link Dialog} carrying `pinnedFooter`, which is what drops the panel's
   * bottom padding: a sticky element is clamped to its containing block, so with that padding in
   * place the row settles above the foot of the panel and content scrolls through the gap. The
   * padding moves in here, where this row's own background covers it. The horizontal offset is
   * the same `p-6`, undone so the background reaches the edges.
   *
   * A popover sets its own, much smaller padding, and would get a footer wider than itself
   * hanging past its bottom edge.
   */
  pinned?: boolean;
  className?: string;
}

/**
 * The single, canonical action row for every modal and confirmation in the app.
 * Routing each footer through here guarantees one layout - right-aligned, with
 * Cancel to the left of the primary action - so dialogs can never drift apart.
 *
 * `pinned` keeps the row at the foot of a panel that scrolls, for a form long enough to need it:
 * the series form used to carry its own Start past the bottom of the viewport. Sticky rather than
 * a fixed bar, so a panel short enough to fit is unchanged. It pairs with `Dialog`'s
 * `pinnedFooter`, and neither works without the other.
 */
export function DialogFooter({
  children,
  pinned = false,
  className,
}: Readonly<DialogFooterProps>) {
  return (
    <div
      className={cn(
        "flex items-center justify-end gap-2",
        pinned && "bg-surface sticky bottom-0 -mx-6 px-6 pt-2 pb-6",
        className,
      )}
    >
      {children}
    </div>
  );
}
