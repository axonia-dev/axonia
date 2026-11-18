import { useState } from "react";

import { FormError } from "@/components/patterns/FormError";
import { Button } from "@/components/ui/Button";
import { DatePicker } from "@/components/ui/DatePicker";
import { useMeetingPlan } from "@/features/mentoring/api/use-mentoring";
import type {
  Journal,
  PlannedMeeting,
} from "@/features/mentoring/domain/mentoring";
import { mentoringErrorMessage } from "@/features/mentoring/mentoring-errors";
import {
  latestPlannableDay,
  MAX_MEETING_TOPICS,
} from "@/features/mentoring/ui/logbook/plan";
import { TopicToggles } from "@/features/mentoring/ui/logbook/TopicToggles";

interface Props {
  journal: Journal;
  /** The upcoming meeting to change; a new one when null. */
  meeting: PlannedMeeting | null;
  /** Called with the id of the meeting saved. */
  onSaved: (meetingId: number) => void;
  onCancel: () => void;
  onRemoved: () => void;
}

/** Plans a meeting, or changes one: its day and the board topics it means to cover. */
export function MeetingEditor({
  journal,
  meeting,
  onSaved,
  onCancel,
  onRemoved,
}: Readonly<Props>) {
  const { plan, change, remove } = useMeetingPlan(journal.pairing.id);
  const save = meeting === null ? plan : change;
  const [day, setDay] = useState(meeting?.on ?? "");
  const [topicIds, setTopicIds] = useState(meeting?.topicIds ?? []);
  const pending = save.isPending || remove.isPending;
  const error = save.error ?? remove.error;

  function submit() {
    const input = { on: day, topicIds };
    if (meeting === null) {
      plan.mutate(input, { onSuccess: (saved) => onSaved(saved.id) });
    } else {
      change.mutate(
        { id: meeting.id, input },
        { onSuccess: () => onSaved(meeting.id) },
      );
    }
  }

  return (
    <div
      data-meeting-editor
      className="border-border-strong bg-background flex flex-col gap-3 rounded-2xl border p-3 sm:p-4"
    >
      <DatePicker
        label="Meeting day"
        value={day}
        onChange={(next) => {
          save.reset();
          setDay(next);
        }}
        minValue={journal.today}
        maxValue={latestPlannableDay(journal.today)}
        className="max-w-56"
      />
      <TopicToggles
        journal={journal}
        selected={topicIds}
        onChange={setTopicIds}
        max={MAX_MEETING_TOPICS}
      />
      <FormError error={error} message={mentoringErrorMessage(error)} />
      <div className="flex flex-wrap justify-end gap-2">
        {meeting !== null && (
          <Button
            variant="ghost"
            className="mr-auto"
            isDisabled={pending}
            onPress={() => remove.mutate(meeting.id, { onSuccess: onRemoved })}
          >
            Remove
          </Button>
        )}
        <Button variant="ghost" onPress={onCancel} isDisabled={pending}>
          Cancel
        </Button>
        <Button isDisabled={day === "" || pending} onPress={submit}>
          Save
        </Button>
      </div>
    </div>
  );
}
