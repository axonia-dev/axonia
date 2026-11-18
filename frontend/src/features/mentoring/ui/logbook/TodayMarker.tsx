import { NotebookPen } from "lucide-react";
import { useEffect, useRef } from "react";

import type { Journal } from "@/features/mentoring/domain/mentoring";
import { NoteComposer } from "@/features/mentoring/ui/logbook/NoteComposer";
import { RailRow } from "@/features/mentoring/ui/logbook/RailRow";
import { longDay } from "@/features/mentoring/ui/meeting-day";

/** A request to write: the meeting day it is for, and a count so asking twice still moves focus. */
export interface Compose {
  on?: string;
  asked: number;
}

interface Props {
  journal: Journal;
  compose: Compose | null;
  onWrite: () => void;
  onClose: () => void;
}

function focusLost(): boolean {
  const active = document.activeElement;
  return active === null || active === document.body;
}

/** Focus enters the composer as it opens and returns to Write a note as it closes. */
function useComposerFocus(compose: Compose | null) {
  const box = useRef<HTMLDivElement>(null);
  const trigger = useRef<HTMLButtonElement>(null);
  const wasOpen = useRef(false);
  useEffect(() => {
    if (compose === null) {
      if (wasOpen.current && focusLost()) trigger.current?.focus();
      wasOpen.current = false;
      return;
    }
    wasOpen.current = true;
    box.current?.scrollIntoView?.({ block: "nearest" });
    box.current?.querySelector("textarea")?.focus();
  }, [compose]);
  return { box, trigger };
}

/** Where the plan ends and what happened begins; notes are written from here. */
export function TodayMarker({
  journal,
  compose,
  onWrite,
  onClose,
}: Readonly<Props>) {
  const { box, trigger } = useComposerFocus(compose);
  const on = compose?.on ?? journal.today;
  return (
    <RailRow dot="today">
      <div className="flex flex-col gap-3">
        <div className="flex flex-wrap items-center gap-x-3 gap-y-2 pt-1.5">
          <span className="text-foreground text-xs font-bold tracking-[0.08em] uppercase">
            Today
          </span>
          <span className="text-muted-foreground text-xs">
            {longDay(journal.today)}
          </span>
          {compose === null && (
            <button
              ref={trigger}
              type="button"
              data-write-note
              onClick={onWrite}
              className="border-border bg-surface-sunken text-foreground hover:border-border-strong focus-visible:ring-focus-ring inline-flex cursor-pointer items-center gap-1.5 rounded-xl border px-3 py-1.5 text-sm font-semibold outline-none focus-visible:ring-2"
            >
              <NotebookPen aria-hidden className="size-4" />
              Write a note
            </button>
          )}
        </div>
        {compose !== null && (
          <div ref={box}>
            <NoteComposer
              key={`${on}-${compose.asked}`}
              journal={journal}
              meetingOn={on}
              onDone={onClose}
            />
          </div>
        )}
      </div>
    </RailRow>
  );
}
