import { useState } from "react";

import { ConfirmPopover } from "@/components/patterns/ConfirmPopover";
import { AreaTag } from "@/components/ui/AreaTag";
import { Button } from "@/components/ui/Button";
import { Dialog } from "@/components/ui/Dialog";
import { DialogFooter } from "@/components/ui/DialogFooter";
import { useJournalWrite } from "@/features/mentoring/api/use-mentoring";
import type {
  Journal,
  Note,
  Topic,
} from "@/features/mentoring/domain/mentoring";
import { STATE_LABELS } from "@/features/mentoring/ui/board/Sticker";
import {
  authorRole,
  notesNaming,
  ROLE_INK,
} from "@/features/mentoring/ui/journal-people";
import { JournalPointers } from "@/features/mentoring/ui/JournalPointers";
import { onFailure } from "@/features/mentoring/ui/notify-failure";
import { personName } from "@/features/mentoring/ui/PartnerFace";
import { TopicEditor } from "@/features/mentoring/ui/TopicEditor";
import { calendarDay } from "@/lib/declared-on";

interface Props {
  journal: Journal;
  topic: Topic | null;
  onClose: () => void;
  /** Called once the topic is removed, before the dialog closes on it. */
  onRemoved?: (topicId: number) => void;
}

type Write = ReturnType<typeof useJournalWrite>;

function Heading({ children }: Readonly<{ children: string }>) {
  return (
    <h3 className="font-display text-foreground text-sm font-semibold">
      {children}
    </h3>
  );
}

function NamingNotes({
  journal,
  notes,
}: Readonly<{ journal: Journal; notes: Note[] }>) {
  if (notes.length === 0) {
    return (
      <p className="text-muted-foreground text-sm">No note names it yet.</p>
    );
  }
  return (
    <ul className="flex flex-col gap-2">
      {notes.map((note) => (
        <li
          key={note.id}
          style={{
            borderColor: ROLE_INK[authorRole(note, journal.pairing.myRole)],
          }}
          className="bg-surface-sunken rounded-lg border-l-4 px-3 py-2"
        >
          <p className="text-muted-foreground text-xs">
            {personName(note.authorName)} · {calendarDay(note.meetingOn)}
          </p>
          <p className="text-foreground text-sm wrap-anywhere whitespace-pre-line">
            {note.body}
          </p>
        </li>
      ))}
    </ul>
  );
}

function RemoveTopic({
  topic,
  write,
  onRemoved,
}: Readonly<{ topic: Topic; write: Write; onRemoved: () => void }>) {
  const [confirming, setConfirming] = useState(false);
  return (
    <div className="relative mr-auto">
      <Button variant="ghost" onPress={() => setConfirming(true)}>
        Remove topic
      </Button>
      <ConfirmPopover
        label={`Confirm removing ${topic.label}`}
        isOpen={confirming}
        confirmLabel="Remove"
        isPending={write.removeTopic.isPending}
        onCancel={() => setConfirming(false)}
        onConfirm={() =>
          write.removeTopic.mutate(topic.id, {
            onSuccess: onRemoved,
            onError: onFailure,
          })
        }
      >
        Remove this topic and its links?
      </ConfirmPopover>
    </div>
  );
}

function Body({
  journal,
  topic,
  onClose,
  onRemoved,
}: Readonly<Props & { topic: Topic }>) {
  const write = useJournalWrite(journal.pairing.id);
  const { writable } = journal;
  return (
    <>
      <div className="flex flex-wrap items-center gap-2">
        <span className="text-muted-foreground text-xs font-semibold">
          {STATE_LABELS[topic.state]}
        </span>
        {topic.area !== null && <AreaTag area={topic.area} size="md" />}
      </div>
      {writable && (
        <TopicEditor
          topic={topic}
          communitySlug={journal.pairing.communitySlug}
          isPending={write.updateTopic.isPending}
          onSave={(label, area) =>
            write.updateTopic.mutate(
              { id: topic.id, topic: { label, area } },
              { onError: onFailure },
            )
          }
        />
      )}
      <section className="flex flex-col gap-2">
        <Heading>Links</Heading>
        {topic.pointers.length === 0 && !writable && (
          <p className="text-muted-foreground text-sm">No links.</p>
        )}
        <JournalPointers
          journal={journal}
          pointers={topic.pointers}
          subject={topic.label}
          on={{ topicId: topic.id, noteId: null }}
        />
      </section>
      <section className="flex flex-col gap-2">
        <Heading>Notes</Heading>
        <NamingNotes journal={journal} notes={notesNaming(journal, topic.id)} />
      </section>
      <DialogFooter>
        {writable && (
          <RemoveTopic
            topic={topic}
            write={write}
            onRemoved={() => {
              onRemoved?.(topic.id);
              onClose();
            }}
          />
        )}
        <Button variant="secondary" onPress={onClose}>
          Close
        </Button>
      </DialogFooter>
    </>
  );
}

/** A topic opened from the board or the trail: its name and area, its links and the notes that name it. */
export function TopicDialog({
  journal,
  topic,
  onClose,
  onRemoved,
}: Readonly<Props>) {
  return (
    <Dialog
      isOpen={topic !== null}
      onOpenChange={(open) => {
        if (!open) onClose();
      }}
      title={topic?.label ?? ""}
    >
      {topic !== null && (
        <Body
          journal={journal}
          topic={topic}
          onClose={onClose}
          {...(onRemoved === undefined ? {} : { onRemoved })}
        />
      )}
    </Dialog>
  );
}
