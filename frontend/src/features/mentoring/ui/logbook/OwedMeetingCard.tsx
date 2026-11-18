import { Ellipsis, Pencil, Trash2 } from "lucide-react";
import { useId } from "react";
import type { Key } from "react-aria-components";

import { MenuButton, MenuItem } from "@/components/ui/Menu";
import { useMeetingPlan } from "@/features/mentoring/api/use-mentoring";
import type {
  Journal,
  PlannedMeeting,
} from "@/features/mentoring/domain/mentoring";
import { removalHandlers } from "@/features/mentoring/ui/logbook/meeting-removal";
import { PlannedTopics } from "@/features/mentoring/ui/logbook/TopicChips";
import { longDay } from "@/features/mentoring/ui/meeting-day";
import { personName } from "@/features/mentoring/ui/PartnerFace";

interface Props {
  journal: Journal;
  meeting: PlannedMeeting;
  onAddNote: (meetingOn: string) => void;
  onRemoved: () => void;
}

/** A planned day that passed with no note: its notes are owed until written or the day is removed. */
export function OwedMeetingCard({
  journal,
  meeting,
  onAddNote,
  onRemoved,
}: Readonly<Props>) {
  const { remove } = useMeetingPlan(journal.pairing.id);
  const day = longDay(meeting.on);
  const titleId = useId();

  function act(key: Key) {
    if (key === "note") onAddNote(meeting.on);
    else remove.mutate(meeting.id, removalHandlers(onRemoved));
  }

  return (
    <article
      aria-labelledby={titleId}
      data-owed-on={meeting.on}
      className="relative flex flex-col gap-2 rounded-2xl border border-dashed border-[color-mix(in_srgb,var(--warning)_60%,var(--border))] bg-[color-mix(in_srgb,var(--warning)_6%,var(--surface))] p-3 pr-12 sm:p-4 sm:pr-12"
    >
      <h3
        id={titleId}
        className="font-display text-foreground flex flex-wrap items-center gap-2 text-base font-semibold"
      >
        {day}
        <span className="text-warning-ink rounded-full bg-[color-mix(in_srgb,var(--warning)_25%,var(--surface))] px-2 py-0.5 font-sans text-[11px] font-semibold">
          Passed
        </span>
      </h3>
      <PlannedTopics journal={journal} topicIds={meeting.topicIds} />
      <p className="text-muted-foreground text-xs">
        Planned by {personName(meeting.plannedByName, meeting.plannedById)}
      </p>
      <MenuButton
        label={`More for ${day}`}
        trigger={<Ellipsis aria-hidden className="size-4" />}
        onAction={act}
        className="text-muted-foreground hover:bg-surface-sunken hover:text-foreground data-[pressed]:bg-surface-sunken absolute top-2 right-2 grid size-8 place-items-center rounded-lg"
      >
        <MenuItem id="note">
          <Pencil aria-hidden className="size-4" />
          Add a note
        </MenuItem>
        <MenuItem id="remove" tone="danger">
          <Trash2 aria-hidden className="size-4" />
          Remove
        </MenuItem>
      </MenuButton>
    </article>
  );
}
