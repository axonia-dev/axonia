import { useJournalWrite } from "@/features/mentoring/api/use-mentoring";
import type { Journal, Pointer } from "@/features/mentoring/domain/mentoring";
import { onFailure } from "@/features/mentoring/ui/notify-failure";
import { Pointers } from "@/features/mentoring/ui/PointerChip";

interface Props {
  journal: Journal;
  pointers: Pointer[];
  subject: string;
  /** What the links hang from: one topic or one note. */
  on: { topicId: number; noteId: null } | { topicId: null; noteId: number };
}

/** The links on a topic or a note of the journal, added and removed while it is writable. */
export function JournalPointers({
  journal,
  pointers,
  subject,
  on,
}: Readonly<Props>) {
  const write = useJournalWrite(journal.pairing.id);
  return (
    <Pointers
      pointers={pointers}
      writable={journal.writable}
      subject={subject}
      isPending={write.addPointer.isPending}
      onRemove={(id) => write.removePointer.mutate(id, { onError: onFailure })}
      onAdd={(url, done, fail) =>
        write.addPointer.mutate(
          { ...on, url },
          { onSuccess: done, onError: fail },
        )
      }
    />
  );
}
