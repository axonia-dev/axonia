import { useContext, useState } from "react";

import { ConfirmPopover } from "@/components/patterns/ConfirmPopover";
import { Button } from "@/components/ui/Button";
import { useMe } from "@/features/identity/api/use-me";
import { useJournalWrite } from "@/features/mentoring/api/use-mentoring";
import type {
  Journal,
  MentoringRole,
  Note,
} from "@/features/mentoring/domain/mentoring";
import { ROLE_INK } from "@/features/mentoring/ui/journal-people";
import { JournalPointers } from "@/features/mentoring/ui/JournalPointers";
import { FollowNote } from "@/features/mentoring/ui/logbook/follow-note";
import type { Meeting } from "@/features/mentoring/ui/logbook/meetings";
import { meetingDay } from "@/features/mentoring/ui/logbook/NextMeetingTicket";
import { NoteComposer } from "@/features/mentoring/ui/logbook/NoteComposer";
import { onFailure } from "@/features/mentoring/ui/notify-failure";
import { PartnerFace, personName } from "@/features/mentoring/ui/PartnerFace";
import { useSwapFocus } from "@/features/mentoring/ui/use-swap-focus";
import { cn } from "@/lib/cn";

interface Props {
  journal: Journal;
  meeting: Meeting;
  onAddNote: (meetingOn: string) => void;
}

function NoteActions({
  journal,
  note,
  onEdit,
}: Readonly<{ journal: Journal; note: Note; onEdit: () => void }>) {
  const { removeNote } = useJournalWrite(journal.pairing.id);
  const [confirming, setConfirming] = useState(false);
  return (
    <div className="-mr-2 ml-auto flex gap-0.5">
      <Button
        variant="ghost"
        size="sm"
        data-swap-trigger
        data-note-edit
        onPress={onEdit}
      >
        Edit
      </Button>
      <Button variant="ghost" size="sm" onPress={() => setConfirming(true)}>
        Remove
      </Button>
      <ConfirmPopover
        label={`Confirm removing the note of ${meetingDay(note.meetingOn)}`}
        isOpen={confirming}
        confirmLabel="Remove"
        isPending={removeNote.isPending}
        onCancel={() => setConfirming(false)}
        onConfirm={() =>
          removeNote.mutate(note.id, {
            onSuccess: () => setConfirming(false),
            onError: onFailure,
          })
        }
      >
        Remove this note?
      </ConfirmPopover>
    </div>
  );
}

function NoteItem({
  journal,
  note,
}: Readonly<{ journal: Journal; note: Note }>) {
  const [editing, setEditing] = useState(false);
  const { box, swap } = useSwapFocus<HTMLLIElement>(editing);
  const followNote = useContext(FollowNote);
  const own = journal.writable && note.mine;

  function edit(next: boolean) {
    swap();
    setEditing(next);
  }

  return (
    <li
      ref={box}
      data-note-id={note.id}
      data-meeting-on={note.meetingOn}
      className="relative flex flex-col gap-1.5"
    >
      {editing ? (
        <NoteComposer
          journal={journal}
          note={note}
          onDone={(saved) => {
            edit(false);
            if (saved !== undefined && saved.meetingOn !== note.meetingOn) {
              followNote({ noteId: note.id, meetingOn: saved.meetingOn ?? "" });
            }
          }}
        />
      ) : (
        <p className="text-foreground text-sm wrap-anywhere whitespace-pre-line">
          {note.body}
          {note.editedAt !== null && (
            <span className="text-muted-foreground text-xs"> · edited</span>
          )}
        </p>
      )}
      <div className="flex items-start gap-2">
        <div className="min-w-0 flex-1">
          <JournalPointers
            journal={journal}
            pointers={note.pointers}
            subject={`the note by ${personName(note.authorName, note.authorId)}`}
            on={{ topicId: null, noteId: note.id }}
          />
        </div>
        {own && !editing && (
          <NoteActions
            journal={journal}
            note={note}
            onEdit={() => edit(true)}
          />
        )}
      </div>
    </li>
  );
}

function Who({
  journal,
  side,
}: Readonly<{ journal: Journal; side: MentoringRole }>) {
  const me = useMe();
  const { pairing } = journal;
  const mine = side === pairing.myRole;
  const person = mine
    ? {
        userId: me.data?.id ?? "",
        name: me.data?.displayName ?? "",
        avatarUrl: me.data?.avatarUrl ?? null,
      }
    : {
        userId: pairing.partnerId,
        name: pairing.partnerName,
        avatarUrl: pairing.partnerAvatarUrl,
      };
  return (
    <p className="text-foreground flex items-center gap-2 text-xs font-semibold">
      <PartnerFace {...person} className="size-6 text-[9px]" />
      <span className="truncate">{personName(person.name, person.userId)}</span>
      <span className="text-muted-foreground font-normal">
        {side === "MENTOR" ? "Mentor" : "Mentee"}
      </span>
    </p>
  );
}

function Half({
  journal,
  meeting,
  side,
  notes,
  onAddNote,
}: Readonly<Props & { side: MentoringRole; notes: Note[] }>) {
  const owner = journal.writable && side === journal.pairing.myRole;
  const empty = notes.length === 0;
  return (
    <div
      className={cn(
        "bg-surface flex flex-col gap-2 rounded-xl border-t-4 p-3",
        empty && "bg-surface/60 border-dashed",
      )}
      style={{ borderTopColor: ROLE_INK[side] }}
    >
      <Who journal={journal} side={side} />
      {empty && owner && (
        <Button
          variant="ghost"
          size="sm"
          className="-ml-3 self-start"
          onPress={() => onAddNote(meeting.on)}
        >
          Add a note for this meeting
        </Button>
      )}
      {empty && !owner && (
        <p className="text-muted-foreground text-sm">No note</p>
      )}
      {!empty && (
        <ul className="flex flex-col gap-3">
          {notes.map((note) => (
            <NoteItem key={note.id} journal={journal} note={note} />
          ))}
        </ul>
      )}
    </div>
  );
}

function TopicChips({ journal, meeting }: Readonly<Omit<Props, "onAddNote">>) {
  const named = meeting.topicIds
    .map((id) => journal.topics.find((topic) => topic.id === id))
    .filter((topic) => topic !== undefined);
  if (named.length === 0) return null;
  return (
    <ul aria-label="Topics" className="flex flex-wrap gap-1.5">
      {named.map((topic) => (
        <li
          key={topic.id}
          className={cn(
            "text-foreground inline-flex max-w-full items-center gap-1 rounded-full px-2.5 py-0.5 text-[11px] font-semibold",
            topic.state === "DONE"
              ? "bg-[color-mix(in_srgb,var(--success)_22%,var(--surface))]"
              : "bg-[color-mix(in_srgb,var(--warning)_20%,var(--surface))]",
          )}
        >
          <span aria-hidden>✦</span>
          <span className="truncate">{topic.label}</span>
        </li>
      ))}
    </ul>
  );
}

/** One meeting: the topics its notes name, and one half per person, the mentor's first. */
export function MeetingCard({ journal, meeting, onAddNote }: Readonly<Props>) {
  return (
    <article
      aria-labelledby={`meeting-${meeting.on}`}
      className="bg-surface-sunken flex flex-col gap-2.5 rounded-2xl p-3 sm:p-4"
    >
      <h3
        id={`meeting-${meeting.on}`}
        className="font-display text-foreground text-base font-semibold"
      >
        Meeting {meeting.number}{" "}
        <span className="text-muted-foreground ml-2 text-sm font-normal sm:sr-only">
          {meetingDay(meeting.on)}
        </span>
      </h3>
      <TopicChips journal={journal} meeting={meeting} />
      <div className="grid grid-cols-1 gap-3 sm:grid-cols-2">
        <Half
          journal={journal}
          meeting={meeting}
          side="MENTOR"
          notes={meeting.mentorNotes}
          onAddNote={onAddNote}
        />
        <Half
          journal={journal}
          meeting={meeting}
          side="MENTEE"
          notes={meeting.menteeNotes}
          onAddNote={onAddNote}
        />
      </div>
    </article>
  );
}
