import { Plus } from "lucide-react";
import { useId, useRef, useState } from "react";

import { Button } from "@/components/ui/Button";
import { useMeetingPlan } from "@/features/mentoring/api/use-mentoring";
import type {
  Journal,
  PlannedMeeting,
} from "@/features/mentoring/domain/mentoring";
import { MeetingEditor } from "@/features/mentoring/ui/logbook/MeetingEditor";
import { upcomingOf } from "@/features/mentoring/ui/logbook/plan";
import { PlannedTopics } from "@/features/mentoring/ui/logbook/TopicChips";
import {
  dayOfMonth,
  longDay,
  longWeekdayOf,
  monthOf,
} from "@/features/mentoring/ui/meeting-day";
import { onFailure } from "@/features/mentoring/ui/notify-failure";
import { personName } from "@/features/mentoring/ui/PartnerFace";
import { useFollowFocus } from "@/features/mentoring/ui/use-follow-focus";

/** The meeting whose editor is open, or the new one. */
type Editing = number | "new" | null;

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

function Ticket({
  journal,
  meeting,
  onChange,
  onRemoved,
}: Readonly<{
  journal: Journal;
  meeting: PlannedMeeting;
  onChange: () => void;
  onRemoved: () => void;
}>) {
  const { remove } = useMeetingPlan(journal.pairing.id);
  const titleId = useId();
  return (
    <div
      role="group"
      aria-labelledby={titleId}
      data-meeting-id={meeting.id}
      className="flex max-w-md items-stretch overflow-hidden rounded-2xl bg-[color-mix(in_srgb,var(--warning)_18%,var(--surface))]"
    >
      <span
        aria-hidden
        className="bg-warning text-warning-foreground flex flex-none flex-col items-center justify-center border-r-[3px] border-dotted border-[var(--surface)] px-4 py-3 text-xs font-bold tracking-[0.08em] uppercase"
      >
        <span className="font-display text-2xl leading-none tracking-normal">
          {dayOfMonth(meeting.on)}
        </span>
        {monthOf(meeting.on)}
      </span>
      <div className="flex min-w-0 flex-col gap-1.5 px-4 py-3">
        <p id={titleId} className="text-foreground text-sm font-semibold">
          Next meeting {longDay(meeting.on)}
        </p>
        <PlannedTopics journal={journal} topicIds={meeting.topicIds} onWarm />
        <p className="text-muted-foreground text-xs">
          Planned by {personName(meeting.plannedByName, meeting.plannedById)}
        </p>
        <div className="-ml-2 flex flex-wrap gap-1">
          <Button
            variant="ghost"
            size="sm"
            data-meeting-open
            onPress={onChange}
          >
            Change
          </Button>
          <Button
            variant="ghost"
            size="sm"
            isDisabled={remove.isPending}
            onPress={() =>
              remove.mutate(meeting.id, {
                onSuccess: onRemoved,
                onError: onFailure,
              })
            }
          >
            Remove
          </Button>
        </div>
      </div>
    </div>
  );
}

function ThenRow({
  journal,
  meeting,
  onOpen,
}: Readonly<{
  journal: Journal;
  meeting: PlannedMeeting;
  onOpen: () => void;
}>) {
  return (
    <div
      data-meeting-id={meeting.id}
      className="relative flex max-w-md items-center gap-2.5 rounded-xl border border-[color-mix(in_srgb,var(--warning)_35%,var(--border))] bg-[color-mix(in_srgb,var(--warning)_7%,var(--surface))] py-1.5 pr-2.5 pl-1.5 hover:border-[color-mix(in_srgb,var(--warning)_60%,var(--border))]"
    >
      <button
        type="button"
        aria-label={`Change ${longDay(meeting.on)}`}
        data-meeting-open
        onClick={onOpen}
        className="focus-visible:ring-focus-ring flex flex-none cursor-pointer items-center gap-2.5 rounded-lg outline-none after:absolute after:inset-0 after:rounded-xl focus-visible:ring-2"
      >
        <span className="w-10 flex-none rounded-lg bg-[color-mix(in_srgb,var(--warning)_55%,var(--surface))] py-1 text-center text-[9px] font-bold tracking-[0.06em] uppercase">
          <span className="font-display block text-base leading-tight tracking-normal">
            {dayOfMonth(meeting.on)}
          </span>
          {monthOf(meeting.on)}
        </span>
        <span className="text-foreground text-sm font-semibold">
          {longWeekdayOf(meeting.on)}
        </span>
      </button>
      <PlannedTopics journal={journal} topicIds={meeting.topicIds} />
    </div>
  );
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

/** The meetings the pair planned ahead: the soonest as a ticket, the later ones under Then. */
export function PlanAhead({ journal }: Readonly<{ journal: Journal }>) {
  const [editing, setEditing] = useState<Editing>(null);
  const box = useRef<HTMLDivElement>(null);
  const focusOn = useFollowFocus(journal.meetings, (want: Landing) =>
    landingIn(box.current, want),
  );
  const [ticket, ...then] = upcomingOf(journal);

  function open(next: Editing) {
    setEditing(next);
    focusOn("editor");
  }

  function closed(landing: Landing) {
    setEditing(null);
    focusOn(landing);
  }

  function editorFor(meeting: PlannedMeeting | null, back: Landing) {
    return (
      <MeetingEditor
        journal={journal}
        meeting={meeting}
        onSaved={closed}
        onCancel={() => closed(back)}
        onRemoved={() => closed("plan")}
      />
    );
  }

  return (
    <div ref={box} className="flex flex-col gap-2">
      {ticket === undefined && (
        <p className="text-muted-foreground pt-1.5 text-sm">
          No meeting planned
        </p>
      )}
      {ticket !== undefined &&
        (editing === ticket.id ? (
          editorFor(ticket, ticket.id)
        ) : (
          <Ticket
            journal={journal}
            meeting={ticket}
            onChange={() => open(ticket.id)}
            onRemoved={() => focusOn({ removed: ticket.id })}
          />
        ))}
      {then.length > 0 && (
        <p className="text-muted-foreground mt-1 text-[11px] font-bold tracking-[0.08em] uppercase">
          Then
        </p>
      )}
      {then.map((meeting) =>
        editing === meeting.id ? (
          <div key={meeting.id}>{editorFor(meeting, meeting.id)}</div>
        ) : (
          <ThenRow
            key={meeting.id}
            journal={journal}
            meeting={meeting}
            onOpen={() => open(meeting.id)}
          />
        ),
      )}
      {editing === "new" ? (
        editorFor(null, "plan")
      ) : (
        <PlanButton onPress={() => open("new")} />
      )}
    </div>
  );
}
