import { CalendarDays, CalendarPlus } from "lucide-react";
import { useEffect, useLayoutEffect, useRef, useState } from "react";
import { createPortal } from "react-dom";

import { menuPopoverClass } from "@/components/ui/Menu";
import type { ScheduleEntry } from "@/features/events/domain/schedule";
import { shortDayLabel } from "@/features/events/ui/schedule-dates";
import type { EntryEditor } from "@/features/events/ui/ScheduleEntryMenu";
import { ScheduleEntryMenu } from "@/features/events/ui/ScheduleEntryMenu";
import {
  ActionList,
  menuIconClass,
} from "@/features/events/ui/ScheduleMenuList";
import { cn } from "@/lib/cn";

export type ContextTarget =
  | { kind: "day"; iso: string }
  | { kind: "entry"; entry: ScheduleEntry; label: string };

/** An open menu: what the right-click landed on, and where the pointer was. Held by whichever
 *  view owns the menu, which both the calendar and the timeline do. */
export interface MenuState {
  target: ContextTarget;
  x: number;
  y: number;
}

interface Props {
  slug: string;
  target: ContextTarget;
  x: number;
  y: number;
  canManage: boolean;
  canStartSeries: boolean;
  onClose: () => void;
  onCreateSeries?: (iso: string) => void;
  onCreateOneOff?: (iso: string) => void;
}

const PAD = 8;

export function ScheduleContextMenu({
  slug,
  target,
  x,
  y,
  canManage,
  canStartSeries,
  onClose,
  onCreateSeries,
  onCreateOneOff,
}: Readonly<Props>) {
  const menuRef = useRef<HTMLDivElement>(null);
  const [pos, setPos] = useState({ top: y, left: x });
  // The menu steps out of sight while an editor it owns is up, and stays mounted: unmounting it
  // here would take that editor down with it, mid-edit.
  const [openEditor, setOpenEditor] = useState<EntryEditor | null>(null);

  useLayoutEffect(() => {
    const el = menuRef.current;
    if (!el) return;
    const { offsetWidth, offsetHeight } = el;
    const clampedLeft = Math.max(
      PAD,
      Math.min(x, window.innerWidth - offsetWidth - PAD),
    );
    const clampedTop = Math.max(
      PAD,
      Math.min(y, window.innerHeight - offsetHeight - PAD),
    );
    setPos({ top: clampedTop, left: clampedLeft });
  }, [x, y]);

  useEffect(() => {
    const onKey = (e: KeyboardEvent) => {
      if (e.key === "Escape") onClose();
    };
    document.addEventListener("keydown", onKey);
    return () => document.removeEventListener("keydown", onKey);
  }, [onClose]);

  return createPortal(
    <>
      <div
        className="fixed inset-0 z-40"
        aria-hidden="true"
        onPointerDown={onClose}
      />
      <div
        ref={menuRef}
        className={cn(
          "fixed z-50",
          menuPopoverClass,
          openEditor !== null && "invisible",
        )}
        style={{ top: pos.top, left: pos.left }}
        onPointerDown={(e) => e.stopPropagation()}
      >
        {target.kind === "day" ? (
          <DayMenu
            iso={target.iso}
            canStartSeries={canStartSeries}
            onClose={onClose}
            {...(onCreateSeries !== undefined && { onCreateSeries })}
            {...(onCreateOneOff !== undefined && { onCreateOneOff })}
          />
        ) : (
          <ScheduleEntryMenu
            slug={slug}
            entry={target.entry}
            label={target.label}
            canManage={canManage}
            openEditor={openEditor}
            onOpenEditor={setOpenEditor}
            onClose={onClose}
          />
        )}
      </div>
    </>,
    document.body,
  );
}

function DayMenu({
  iso,
  canStartSeries,
  onClose,
  onCreateSeries,
  onCreateOneOff,
}: Readonly<{
  iso: string;
  canStartSeries: boolean;
  onClose: () => void;
  onCreateSeries?: (iso: string) => void;
  onCreateOneOff?: (iso: string) => void;
}>) {
  return (
    <ActionList
      title={{ primary: shortDayLabel(iso) }}
      groups={[
        {
          actions: canStartSeries
            ? [
                {
                  id: "new-series",
                  label: "New series",
                  icon: <CalendarPlus className={menuIconClass} />,
                },
                {
                  id: "one-off",
                  label: "One-off session",
                  icon: <CalendarDays className={menuIconClass} />,
                },
              ]
            : [],
        },
      ]}
      onAction={(key) => {
        if (key === "new-series") onCreateSeries?.(iso);
        if (key === "one-off") onCreateOneOff?.(iso);
        onClose();
      }}
    />
  );
}

/**
 * Forwards a view's optional create-callbacks to the menu. Accepts them as `T | undefined`
 * (explicit union) so the caller can pass them straight through without conditional spreads,
 * keeping its own cyclomatic complexity in check.
 */
export function ContextMenuAdapter({
  slug,
  menu,
  canManage,
  canStartSeries,
  onClose,
  onCreateSeries,
  onCreateOneOff,
}: Readonly<{
  slug: string;
  menu: MenuState;
  canManage: boolean;
  canStartSeries: boolean;
  onClose: () => void;
  onCreateSeries?: ((iso: string) => void) | undefined;
  onCreateOneOff?: ((iso: string) => void) | undefined;
}>) {
  return (
    <ScheduleContextMenu
      slug={slug}
      target={menu.target}
      x={menu.x}
      y={menu.y}
      canManage={canManage}
      canStartSeries={canStartSeries}
      onClose={onClose}
      {...(onCreateSeries && { onCreateSeries })}
      {...(onCreateOneOff && { onCreateOneOff })}
    />
  );
}
