import type { ReactNode } from "react";
import {
  Tooltip as AriaTooltip,
  type TooltipProps as AriaTooltipProps,
} from "react-aria-components";

const tooltipClass =
  "bg-foreground text-background z-20 rounded-md px-2 py-1 text-xs font-medium whitespace-nowrap shadow-md outline-none data-[entering]:opacity-0 data-[exiting]:opacity-0 motion-safe:transition-opacity motion-reduce:transition-none";

interface TooltipProps extends Pick<AriaTooltipProps, "placement" | "offset"> {
  children: ReactNode;
}

/**
 * A hover/focus-triggered label. React Aria portals it to `document.body` and
 * handles the show/hide delay, positioning and `aria-describedby` wiring, so unlike
 * a hand-rolled absolutely positioned span it never becomes part of a trigger's
 * ancestor's scrollable content. Pair with `TooltipTrigger` from
 * `react-aria-components`.
 */
export function Tooltip({ children, ...props }: Readonly<TooltipProps>) {
  return (
    <AriaTooltip {...props} className={tooltipClass}>
      {children}
    </AriaTooltip>
  );
}
