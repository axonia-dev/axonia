import { useRef, useState } from "react";

import { FormError } from "@/components/patterns/FormError";
import { Button } from "@/components/ui/Button";
import { DatePicker } from "@/components/ui/DatePicker";
import { useMeetingPlan } from "@/features/mentoring/api/use-mentoring";
import type {
  Journal,
  PlannedMeeting,
} from "@/features/mentoring/domain/mentoring";
import { mentoringErrorMessage } from "@/features/mentoring/mentoring-errors";
import { leadingMeeting } from "@/features/mentoring/ui/leading-meeting";
import {
  dayOfMonth,
  monthOf,
  weekdayOf,
} from "@/features/mentoring/ui/meeting-day";
import { onFailure } from "@/features/mentoring/ui/notify-failure";
import { personName } from "@/features/mentoring/ui/PartnerFace";
import { useFollowFocus } from "@/features/mentoring/ui/use-follow-focus";

interface Props {
  journal: Journal;
  onWriteNotes: (meetingOn: string) => void;
}

type NextMeeting = PlannedMeeting;

export function meetingDay(day: string): string {
  return `${weekdayOf(day)} ${dayOfMonth(day)} ${monthOf(day)}`;
}

const TICKET =
  "flex max-w-md items-stretch overflow-hidden rounded-2xl bg-[color-mix(in_srgb,var(--warning)_18%,var(--surface))]";

function DayPicker({
  journal,
  meeting,
  initial,
  submitLabel,
  onSaved,
  onCancel,
}: Readonly<{
  journal: Journal;
  meeting: NextMeeting | null;
  initial: string;
  submitLabel: string;
  onSaved: () => void;
  onCancel?: () => void;
}>) {
  const { plan, change } = useMeetingPlan(journal.pairing.id);
  const set = meeting === null ? plan : change;
  const [day, setDay] = useState(initial);
  return (
    <div className="flex flex-col gap-2">
      <div className="flex flex-wrap items-end gap-2">
        <DatePicker
          label="Next meeting"
          value={day}
          onChange={(next) => {
            set.reset();
            setDay(next);
          }}
          minValue={journal.today}
          className="max-w-56"
        />
        <Button
          isDisabled={day === "" || set.isPending}
          onPress={() => {
            const input = { on: day, topicIds: meeting?.topicIds ?? [] };
            if (meeting === null) plan.mutate(input, { onSuccess: onSaved });
            else {
              change.mutate({ id: meeting.id, input }, { onSuccess: onSaved });
            }
          }}
        >
          {submitLabel}
        </Button>
        {onCancel !== undefined && (
          <Button variant="ghost" onPress={onCancel} isDisabled={set.isPending}>
            Cancel
          </Button>
        )}
      </div>
      <FormError error={set.error} message={mentoringErrorMessage(set.error)} />
    </div>
  );
}

function Stub({ on }: Readonly<{ on: string }>) {
  return (
    <span
      aria-hidden
      className="bg-warning text-warning-foreground flex flex-none flex-col items-center justify-center border-r-[3px] border-dotted border-[var(--surface)] px-4 py-3 text-xs font-bold tracking-[0.08em] uppercase"
    >
      <span className="font-display text-2xl leading-none tracking-normal">
        {dayOfMonth(on)}
      </span>
      {monthOf(on)}
    </span>
  );
}

function Ticket({
  journal,
  next,
  onChange,
  onCleared,
  onWriteNotes,
}: Readonly<{
  journal: Journal;
  next: NextMeeting;
  onChange: () => void;
  onCleared: () => void;
  onWriteNotes: (meetingOn: string) => void;
}>) {
  const { remove } = useMeetingPlan(journal.pairing.id);
  return (
    <div className={TICKET}>
      <Stub on={next.on} />
      <div className="flex min-w-0 flex-col gap-1 px-4 py-3">
        <p className="text-foreground text-sm font-semibold">
          Next meeting {meetingDay(next.on)}
          {next.passed && (
            <span className="text-warning-ink ml-2 rounded-full bg-[color-mix(in_srgb,var(--warning)_30%,var(--surface))] px-2 py-0.5 text-xs">
              Passed
            </span>
          )}
        </p>
        <p className="text-muted-foreground text-xs">
          Set by {personName(next.plannedByName, next.plannedById)}
        </p>
        <div className="-ml-2 flex flex-wrap gap-1">
          {next.passed && (
            <Button size="sm" onPress={() => onWriteNotes(next.on)}>
              Write the notes for {meetingDay(next.on)}
            </Button>
          )}
          <Button variant="ghost" size="sm" onPress={onChange}>
            Change
          </Button>
          <Button
            variant="ghost"
            size="sm"
            isDisabled={remove.isPending}
            onPress={() =>
              remove.mutate(next.id, {
                onSuccess: onCleared,
                onError: onFailure,
              })
            }
          >
            Clear
          </Button>
        </div>
      </div>
    </div>
  );
}

type Landing = "Change" | "Set" | "picker";

/** Where focus goes once the ticket and its picker have swapped places. */
function landingIn(box: HTMLElement | null, want: Landing): HTMLElement | null {
  if (box === null) return null;
  if (want === "picker") {
    return box.querySelector<HTMLElement>('[role="spinbutton"]');
  }
  return (
    [...box.querySelectorAll<HTMLElement>("button")].find(
      (button) => button.textContent === want,
    ) ?? null
  );
}

/** The one date the pair agreed to meet next; either of them sets, changes or clears it. */
export function NextMeetingTicket({ journal, onWriteNotes }: Readonly<Props>) {
  const [changing, setChanging] = useState(false);
  const box = useRef<HTMLDivElement>(null);
  const next = leadingMeeting(journal);
  const focusOn = useFollowFocus(`${next?.on}|${changing}`, (want: Landing) =>
    landingIn(box.current, want),
  );
  if (!journal.writable) return null;
  return <div ref={box}>{face()}</div>;

  function face() {
    if (next === null) {
      return (
        <div className="flex flex-col gap-2">
          <p className="text-foreground text-sm font-semibold">
            Set the next meeting
          </p>
          <DayPicker
            journal={journal}
            meeting={null}
            initial={journal.today}
            submitLabel="Set"
            onSaved={() => focusOn("Change")}
          />
        </div>
      );
    }
    if (changing) {
      const close = () => {
        setChanging(false);
        focusOn("Change");
      };
      return (
        <DayPicker
          journal={journal}
          meeting={next}
          initial={next.passed ? journal.today : next.on}
          submitLabel="Save"
          onSaved={close}
          onCancel={close}
        />
      );
    }
    return (
      <Ticket
        journal={journal}
        next={next}
        onChange={() => {
          setChanging(true);
          focusOn("picker");
        }}
        onCleared={() => focusOn("Set")}
        onWriteNotes={onWriteNotes}
      />
    );
  }
}
