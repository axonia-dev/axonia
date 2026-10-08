import { Bell } from "lucide-react";
import { useRef } from "react";
import {
  Button as AriaButton,
  DialogTrigger,
  Group,
  Popover,
} from "react-aria-components";

import { iconButtonClass } from "@/components/ui/IconButton";

import { useUnreadCount } from "../api/use-unread-count";
import { NotificationPanel } from "./NotificationPanel";

// react-aria sizes a popover to the space left on screen, which for a long list is the whole
// viewport. This is the cap its own measurement is taken as the minimum of; the panel's list
// carries the matching `overflow-y-auto`, since the cap alone clips nothing.
const PANEL_MAX_HEIGHT = 480;
const BADGE_CAP = 99;

const panelPopoverClass =
  "flex w-[min(22rem,calc(100vw-1.5rem))] flex-col overflow-hidden rounded-md border border-border-strong bg-surface shadow-lg outline-none transition data-[entering]:opacity-0 data-[entering]:-translate-y-1 data-[exiting]:opacity-0 data-[exiting]:-translate-y-1 motion-reduce:transition-none";

/** The count belongs in the trigger's name, not only on its face: the badge is decorative markup a
 *  screen reader is told to skip. */
function triggerLabel(unread: number): string {
  if (unread === 0) return "Notifications";
  return `Notifications, ${unread} unread`;
}

/**
 * The shell header's way into {@link NotificationPanel}, and the only place the unread count is
 * shown.
 *
 * The badge overhangs the button, so the two are wrapped in a `Group` and the popover is anchored
 * to that instead: anchored to the button alone it would position against the wrong box and sit a
 * few pixels off under a badge that is part of the same control.
 */
export function NotificationBell() {
  const { data } = useUnreadCount();
  const unread = data ?? 0;
  const triggerRef = useRef<HTMLDivElement>(null);
  return (
    <DialogTrigger>
      {/* `role="presentation"`: a Group defaults to `role="group"`, and an unlabelled group holding
          one button and an aria-hidden badge only makes a screen reader announce entering and
          leaving it. The element still earns its place as the popover's anchor box. */}
      <Group
        role="presentation"
        ref={triggerRef}
        className="relative inline-flex flex-none"
      >
        <AriaButton
          aria-label={triggerLabel(unread)}
          className={iconButtonClass()}
        >
          <Bell aria-hidden />
        </AriaButton>
        {unread > 0 && (
          <span
            aria-hidden
            className="bg-primary text-primary-foreground pointer-events-none absolute -top-0.5 -right-0.5 inline-flex h-4 min-w-4 items-center justify-center rounded-full px-1 text-[10px] leading-none font-semibold"
          >
            {unread > BADGE_CAP ? `${BADGE_CAP}+` : unread}
          </span>
        )}
      </Group>
      <Popover
        triggerRef={triggerRef}
        placement="bottom end"
        maxHeight={PANEL_MAX_HEIGHT}
        className={panelPopoverClass}
      >
        <NotificationPanel />
      </Popover>
    </DialogTrigger>
  );
}
