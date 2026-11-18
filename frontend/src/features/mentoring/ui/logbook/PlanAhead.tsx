import { Plus } from "lucide-react";
import { type ReactNode, useLayoutEffect, useRef } from "react";

import { notify } from "@/components/ui/toast";
import type { Journal } from "@/features/mentoring/domain/mentoring";
import { MEETING_PASSED } from "@/features/mentoring/mentoring-errors";
import { MeetingEditor } from "@/features/mentoring/ui/logbook/MeetingEditor";
import {
  type Gone,
  goneWhy,
  upcomingOf,
} from "@/features/mentoring/ui/logbook/plan";
import { ThenRow, Ticket } from "@/features/mentoring/ui/logbook/PlannedRows";
import { useFollowFocus } from "@/features/mentoring/ui/use-follow-focus";

/** The meeting whose editor is open, or the new one. */
export type Editing = number | "new" | null;

const GONE = new Map<Gone, string>([
  ["passed", MEETING_PASSED],
  ["held", "This meeting has its notes now."],
  ["removed", "This meeting was removed."],
]);

/** Where focus goes once a meeting and its editor have swapped places, or a meeting has left. */
type Landing = number | "plan" | "editor" | { removed: number };

function meetingIn(box: HTMLElement, id: number): HTMLElement | null {
  return box.querySelector<HTMLElement>(`[data-meeting-id="${id}"]`);
}

function landingIn(box: HTMLElement | null, want: Landing): HTMLElement | null {
  if (box === null) return null;
  if (typeof want === "number") {
    return meetingIn(box, want)?.querySelector("[data-meeting-open]") ?? null;
  }
  if (want === "editor") {
    return box.querySelector('[data-meeting-editor] [role="spinbutton"]');
  }
  if (want !== "plan" && meetingIn(box, want.removed) !== null) return null;
  return box.querySelector("[data-plan]");
}

function PlanButton({ onPress }: Readonly<{ onPress: () => void }>) {
  return (
    <button
      type="button"
      data-plan
      onClick={onPress}
      className="border-border-strong bg-surface text-foreground hover:border-control-edge focus-visible:ring-focus-ring inline-flex cursor-pointer items-center gap-1.5 self-start rounded-xl border border-dashed px-3 py-2 text-sm font-semibold outline-none focus-visible:ring-2"
    >
      <Plus aria-hidden className="size-4" />
      Plan a meeting
    </button>
  );
}

function Then() {
  return (
    <p className="text-muted-foreground mt-1 text-[11px] font-bold tracking-[0.08em] uppercase">
      Then
    </p>
  );
}

interface Props {
  journal: Journal;
  editing: Editing;
  onEditing: (next: Editing) => void;
}

/** The meetings the pair planned ahead: the soonest as a ticket, the later ones under Then. */
export function PlanAhead({ journal, editing, onEditing }: Readonly<Props>) {
  const box = useRef<HTMLDivElement>(null);
  const focusOn = useFollowFocus(journal.meetings, (want: Landing) =>
    landingIn(box.current, want),
  );
  const upcoming = upcomingOf(journal);
  const vanished =
    typeof editing === "number" &&
    !upcoming.some((meeting) => meeting.id === editing);
  const editedOn = useRef<string | undefined>(undefined);

  function gone(why: Gone) {
    onEditing(null);
    focusOn("plan");
    notify(GONE.get(why) ?? "");
  }

  useLayoutEffect(() => {
    if (typeof editing !== "number") return;
    if (vanished) gone(goneWhy(journal, editing, editedOn.current));
    else editedOn.current = upcoming.find(({ id }) => id === editing)?.on;
  });

  function open(next: Editing) {
    onEditing(next);
    focusOn("editor");
  }

  function closed(landing: Landing) {
    onEditing(null);
    focusOn(landing);
  }

  // One keyed list, so an open editor keeps its draft when its meeting moves between ticket and Then.
  const rows: ReactNode[] = [];
  upcoming.forEach((meeting, index) => {
    if (index === 1) rows.push(<Then key="then" />);
    if (editing === meeting.id) {
      rows.push(
        <MeetingEditor
          key={meeting.id}
          journal={journal}
          meeting={meeting}
          onSaved={closed}
          onCancel={() => closed(meeting.id)}
          onRemoved={() => closed("plan")}
          onGone={gone}
        />,
      );
    } else if (index === 0) {
      rows.push(
        <Ticket
          key={meeting.id}
          journal={journal}
          meeting={meeting}
          onChange={() => open(meeting.id)}
          onRemoved={() => focusOn({ removed: meeting.id })}
        />,
      );
    } else {
      rows.push(
        <ThenRow
          key={meeting.id}
          journal={journal}
          meeting={meeting}
          onOpen={() => open(meeting.id)}
        />,
      );
    }
  });

  return (
    <div ref={box} className="flex flex-col gap-2">
      {upcoming.length === 0 && (
        <p className="text-muted-foreground pt-1.5 text-sm">
          No meeting planned
        </p>
      )}
      {rows}
      {editing === "new" ? (
        <MeetingEditor
          journal={journal}
          meeting={null}
          onSaved={closed}
          onCancel={() => closed("plan")}
          onRemoved={() => closed("plan")}
          onGone={gone}
        />
      ) : (
        <PlanButton onPress={() => open("new")} />
      )}
    </div>
  );
}
