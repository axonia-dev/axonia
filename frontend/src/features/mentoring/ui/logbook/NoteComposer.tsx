import { useState } from "react";

import { Button } from "@/components/ui/Button";
import { DatePicker } from "@/components/ui/DatePicker";
import { TextArea } from "@/components/ui/TextArea";
import { useJournalWrite } from "@/features/mentoring/api/use-mentoring";
import type { Journal, Note } from "@/features/mentoring/domain/mentoring";
import type { NoteInput } from "@/features/mentoring/domain/mentoring-gateway";
import { MEETING_DAY_RANGE } from "@/features/mentoring/mentoring-errors";
import { plannedTopicsOn } from "@/features/mentoring/ui/logbook/plan";
import { TopicToggles } from "@/features/mentoring/ui/logbook/TopicToggles";
import { onFailure } from "@/features/mentoring/ui/notify-failure";

const MAX_NOTE_LENGTH = 2000;

interface Props {
  journal: Journal;
  /** The meeting the note is about; today when none is given. */
  meetingOn?: string;
  /** A note of one's own to change rather than a new one. */
  note?: Note;
  /** Called with what was saved, or with nothing on Cancel. */
  onDone: (saved?: NoteInput) => void;
}

/** A note to change starts from itself; a new one from the day asked for, its topics left to the plan. */
function startOf(journal: Journal, note?: Note, meetingOn?: string) {
  if (note !== undefined) {
    return { body: note.body, day: note.meetingOn, topicIds: note.topicIds };
  }
  return { body: "", day: meetingOn ?? journal.today, topicIds: undefined };
}

function withinPairing(day: string, journal: Journal): boolean {
  return day !== "" && day >= journal.startedOn && day <= journal.today;
}

/** Writes a note about one meeting: what was said, the day it met, the topics it touched. */
export function NoteComposer({
  journal,
  meetingOn,
  note,
  onDone,
}: Readonly<Props>) {
  const write = useJournalWrite(journal.pairing.id);
  const start = startOf(journal, note, meetingOn);
  const [body, setBody] = useState(start.body);
  const [day, setDay] = useState(start.day);
  const [chosen, setChosen] = useState(start.topicIds);
  // A new note follows the plan of the day picked until its topics are chosen by hand.
  const topicIds = chosen ?? plannedTopicsOn(journal, day);
  const pending = write.addNote.isPending || write.updateNote.isPending;
  const dayValid = withinPairing(day, journal);
  const ready = body.trim() !== "" && dayValid && !pending;

  function submit() {
    const input: NoteInput = { body: body.trim(), meetingOn: day, topicIds };
    const handlers = { onSuccess: () => onDone(input), onError: onFailure };
    if (note === undefined) write.addNote.mutate(input, handlers);
    else write.updateNote.mutate({ id: note.id, note: input }, handlers);
  }

  return (
    <div className="border-border-strong bg-background flex flex-col gap-3 rounded-2xl border p-3 sm:p-4">
      <TextArea
        label="Note"
        value={body}
        onChange={setBody}
        maxLength={MAX_NOTE_LENGTH}
        rows={3}
      />
      <DatePicker
        label="Meeting day"
        value={day}
        onChange={setDay}
        minValue={journal.startedOn}
        maxValue={journal.today}
        isInvalid={!dayValid}
        className="max-w-56"
      />
      {!dayValid && (
        <p className="text-danger -mt-2 text-sm">{MEETING_DAY_RANGE}</p>
      )}
      <TopicToggles
        journal={journal}
        selected={topicIds}
        onChange={setChosen}
      />
      <div className="flex justify-end gap-2">
        <Button variant="ghost" onPress={() => onDone()} isDisabled={pending}>
          Cancel
        </Button>
        <Button isDisabled={!ready} onPress={submit}>
          {note === undefined ? "Add note" : "Save note"}
        </Button>
      </div>
    </div>
  );
}
