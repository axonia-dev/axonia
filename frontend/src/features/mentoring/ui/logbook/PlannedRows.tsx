import { useId } from "react";

import { Button } from "@/components/ui/Button";
import { useMeetingPlan } from "@/features/mentoring/api/use-mentoring";
import type {
  Journal,
  PlannedMeeting,
} from "@/features/mentoring/domain/mentoring";
import { removalHandlers } from "@/features/mentoring/ui/logbook/meeting-removal";
import { PlannedTopics } from "@/features/mentoring/ui/logbook/TopicChips";
import {
  dayOfMonth,
  longDay,
  longWeekdayOf,
  monthOf,
} from "@/features/mentoring/ui/meeting-day";
import { personName } from "@/features/mentoring/ui/PartnerFace";

/** The soonest upcoming meeting, as the ticket the pair holds for it. */
export function Ticket({
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
              remove.mutate(meeting.id, removalHandlers(onRemoved))
            }
          >
            Remove
          </Button>
        </div>
      </div>
    </div>
  );
}

/** A later upcoming meeting, one slim row that opens its editor. */
export function ThenRow({
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
      className="has-[:focus-visible]:ring-focus-ring relative flex max-w-md items-center gap-2.5 rounded-xl border border-[color-mix(in_srgb,var(--warning)_35%,var(--border))] bg-[color-mix(in_srgb,var(--warning)_7%,var(--surface))] py-1.5 pr-2.5 pl-1.5 hover:border-[color-mix(in_srgb,var(--warning)_60%,var(--border))] has-[:focus-visible]:ring-2"
    >
      <button
        type="button"
        aria-label={`Change ${longDay(meeting.on)}`}
        data-meeting-open
        onClick={onOpen}
        className="flex flex-none cursor-pointer items-center gap-2.5 rounded-lg outline-none after:absolute after:inset-0 after:rounded-xl"
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
