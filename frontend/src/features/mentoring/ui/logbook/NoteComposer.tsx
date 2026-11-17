import { useId, useState } from "react";
import { Label, ToggleButton, ToggleButtonGroup } from "react-aria-components";

import { Button } from "@/components/ui/Button";
import { DatePicker } from "@/components/ui/DatePicker";
import { TextArea } from "@/components/ui/TextArea";
import { useJournalWrite } from "@/features/mentoring/api/use-mentoring";
import type { Journal, Note } from "@/features/mentoring/domain/mentoring";
import type { NoteInput } from "@/features/mentoring/domain/mentoring-gateway";
import { MEETING_DAY_RANGE } from "@/features/mentoring/mentoring-errors";
import { onFailure } from "@/features/mentoring/ui/notify-failure";
import { trailOrder } from "@/features/mentoring/ui/trail/trail-layout";

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

function draftOf(journal: Journal, note?: Note, meetingOn?: string): NoteInput {
  if (note !== undefined) {
    return {
      body: note.body,
      meetingOn: note.meetingOn,
      topicIds: note.topicIds,
    };
  }
  return { body: "", meetingOn: meetingOn ?? journal.today, topicIds: [] };
}

function withinPairing(day: string, journal: Journal): boolean {
  return day !== "" && day >= journal.startedOn && day <= journal.today;
}

function TopicToggles({
  journal,
  selected,
  onChange,
}: Readonly<{
  journal: Journal;
  selected: number[];
  onChange: (topicIds: number[]) => void;
}>) {
  const labelId = useId();
  if (journal.topics.length === 0) return null;
  return (
    <div className="flex flex-col gap-1.5">
      <Label id={labelId} className="text-foreground text-sm font-medium">
        Topics it names
      </Label>
      <ToggleButtonGroup
        aria-labelledby={labelId}
        selectionMode="multiple"
        selectedKeys={selected}
        onSelectionChange={(keys) => onChange([...keys].map(Number))}
        className="flex flex-wrap gap-1.5"
      >
        {trailOrder(journal.topics).map((topic) => (
          <ToggleButton
            key={topic.id}
            id={topic.id}
            className="border-border bg-surface text-muted-foreground hover:border-border-strong focus-visible:ring-focus-ring data-[selected]:text-foreground inline-flex max-w-full cursor-pointer items-center gap-1 rounded-full border px-2.5 py-1 text-xs font-semibold outline-none focus-visible:ring-2 data-[selected]:border-transparent data-[selected]:bg-[color-mix(in_srgb,var(--warning)_24%,var(--surface))]"
          >
            <span aria-hidden>✦</span>
            <span className="truncate">{topic.label}</span>
          </ToggleButton>
        ))}
      </ToggleButtonGroup>
    </div>
  );
}

/** Writes a note about one meeting: what was said, the day it met, the topics it touched. */
export function NoteComposer({
  journal,
  meetingOn,
  note,
  onDone,
}: Readonly<Props>) {
  const write = useJournalWrite(journal.pairing.id);
  const draft = draftOf(journal, note, meetingOn);
  const [body, setBody] = useState(draft.body);
  const [day, setDay] = useState(draft.meetingOn ?? journal.today);
  const [topicIds, setTopicIds] = useState(draft.topicIds);
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
        onChange={setTopicIds}
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
