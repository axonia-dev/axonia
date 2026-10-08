import type { ReactNode } from "react";
import { NavLink } from "react-router-dom";

import {
  segmentedGroupClass,
  segmentedItemClass,
  segmentedSelectedClass,
} from "@/components/ui/segmented-styles";
import { cn } from "@/lib/cn";

interface SegmentedNavItem {
  to: string;
  label: string;
  icon?: ReactNode;
  /** Rendered after the label, for a destination carrying something waiting. */
  badge?: ReactNode;
}

interface SegmentedNavProps {
  /** Accessible name for the navigation landmark. */
  label: string;
  items: ReadonlyArray<SegmentedNavItem>;
}

/**
 * A segmented control whose options are destinations rather than state. Visually
 * identical to {@link SegmentedControl} and sharing its classes, but each option is a
 * link, so middle-click, the back button and deep links all work and a screen reader
 * announces a place to go rather than a button to press.
 */
export function SegmentedNav({ label, items }: Readonly<SegmentedNavProps>) {
  return (
    <nav aria-label={label} className={segmentedGroupClass}>
      {items.map((item) => (
        <NavLink
          key={item.to}
          to={item.to}
          end
          className={({ isActive }) =>
            cn(
              segmentedItemClass,
              "flex items-center gap-1.5 no-underline",
              isActive && segmentedSelectedClass,
            )
          }
        >
          {({ isActive }) => (
            <>
              {item.icon !== undefined && (
                <span
                  aria-hidden
                  className={cn("flex shrink-0", isActive && "text-primary")}
                >
                  {item.icon}
                </span>
              )}
              {item.label}
              {/* An explicit space, so the accessible name reads as two words rather than
                  running the label into the badge. A whitespace-only anonymous flex item
                  is not rendered, so the gap between them stays the track's own. */}
              {item.badge !== undefined && <> {item.badge}</>}
            </>
          )}
        </NavLink>
      ))}
    </nav>
  );
}
