import type { Key } from "react-aria-components";

import {
  useCancelSession,
  useSession,
} from "@/features/events/api/use-session";
import {
  useSkipOccurrence,
  useUnskipOccurrence,
} from "@/features/events/api/use-sessions";
import type { RescheduleTarget } from "@/features/events/domain/reschedule-target";
import {
  targetFromEntry,
  targetFromSession,
} from "@/features/events/domain/reschedule-target";
import type { ScheduleEntry } from "@/features/events/domain/schedule";
import type { Session } from "@/features/events/domain/session";
import { useCalendarReschedule } from "@/features/events/ui/CalendarDnd";
import { EditSeriesDialog } from "@/features/events/ui/EditSeriesDialog";
import { EndSeriesDialog } from "@/features/events/ui/EndSeriesDialog";
import { entryGroups, entryTitle } from "@/features/events/ui/entry-commands";
import { RescheduleDialog } from "@/features/events/ui/RescheduleDialog";
import { ActionList } from "@/features/events/ui/ScheduleMenuList";
import { useNow } from "@/lib/use-wake-at";

/** The series occurrence a skip or an unskip names, or null for an entry that is neither. */
function occurrenceOf(
  entry: ScheduleEntry,
): { seriesId: number; occurrenceDate: string } | null {
  if (entry.seriesId === null || entry.occurrenceDate === null) return null;
  return { seriesId: entry.seriesId, occurrenceDate: entry.occurrenceDate };
}

/**
 * What the dialog edits, read from the occurrence's own row whenever there is one.
 *
 * A real occurrence is fetched rather than projected, because `PUT /sessions/{id}` replaces the
 * session wholesale while the schedule projection carries neither the facilitator nor the
 * difference between an overridden place and an inherited one - its `place` is the resolved one.
 * Seeded from the chip, a confirm posted an empty place and a null facilitator: a room somebody
 * had set for this one occurrence reverted to the series', whoever was running it was wiped, and
 * the dialog said so nowhere - it showed the occurrence's own place under a line calling it the
 * series' standing one. The calendar drag has always read the row for the same reason.
 *
 * A ghost has no row to read and neither fact to lose: its place is its series' by construction
 * and it has no facilitator. That is what {@link targetFromEntry} is for, and all it is for.
 */
function rescheduleTargetOf(
  entry: ScheduleEntry,
  session: Session | undefined,
): RescheduleTarget | null {
  if (entry.sessionId === null) return targetFromEntry(entry);
  return session === undefined ? null : targetFromSession(session);
}

/** Which of the menu's dialogs is up, or none. */
export type EntryEditor = "reschedule" | "series" | "end-series";

interface Props {
  slug: string;
  entry: ScheduleEntry;
  /** What the chip or row this menu opened from already calls the occurrence. Passed in rather
   *  than derived: a menu naming it a third way could disagree with the thing it opened from. */
  label: string;
  canManage: boolean;
  /** Which dialog this menu owns is up. Held by the container, which steps out of sight while one
   *  is, and which cannot unmount this menu without taking the dialog with it. */
  openEditor: EntryEditor | null;
  onOpenEditor: (editor: EntryEditor) => void;
  onClose: () => void;
}

/** What a right-click on a scheduled occurrence offers its organizer. */
export function ScheduleEntryMenu({
  slug,
  entry,
  label,
  canManage,
  openEditor,
  onOpenEditor,
  onClose,
}: Readonly<Props>) {
  // Read as the menu opens rather than as the dialog does, so the editor has the occurrence's own
  // facts the moment it appears. A read and never a write: nothing is materialised here, and an
  // abandoned edit leaves the projection exactly as it was. Until it lands - and for good if it
  // fails - `target` is null, which is what holds the Reschedule item out of reach.
  const session = useSession(canManage ? entry.sessionId : null);
  const reschedule = useCalendarReschedule(slug);
  const skip = useSkipOccurrence(slug);
  const unskip = useUnskipOccurrence(slug);
  const cancel = useCancelSession(slug);

  function handleAction(key: Key) {
    if (key === "reschedule") return onOpenEditor("reschedule");
    if (key === "edit-series") return onOpenEditor("series");
    if (key === "end-series") return onOpenEditor("end-series");
    const occurrence = occurrenceOf(entry);
    if (key === "skip" && occurrence) skip.mutate(occurrence);
    if (key === "unskip" && occurrence) unskip.mutate(occurrence);
    if (key === "cancel" && entry.sessionId !== null) {
      cancel.mutate(entry.sessionId);
    }
    onClose();
  }

  const now = useNow();
  const target = canManage ? rescheduleTargetOf(entry, session.data) : null;

  return (
    <>
      <ActionList
        title={entryTitle(entry, label)}
        groups={entryGroups(entry, canManage, target !== null, now)}
        {...(session.isError
          ? { note: "This session could not be read." }
          : {})}
        onAction={handleAction}
      />
      {/* Mounted on the press rather than held open at zero: it reads the community's series to
          seed itself, and a right-click that only ever wanted Skip has no business paying for
          that read. */}
      {entry.seriesId !== null && openEditor === "end-series" && (
        <EndSeriesDialog
          slug={slug}
          seriesId={entry.seriesId}
          isOpen
          onOpenChange={(isOpen) => {
            if (!isOpen) onClose();
          }}
        />
      )}
      {entry.seriesId !== null && openEditor === "series" && (
        <EditSeriesDialog
          slug={slug}
          seriesId={entry.seriesId}
          isOpen
          onOpenChange={(isOpen) => {
            if (!isOpen) onClose();
          }}
        />
      )}
      {target !== null && (
        <RescheduleDialog
          slug={slug}
          isOpen={openEditor === "reschedule"}
          onOpenChange={(isOpen) => {
            if (!isOpen) onClose();
          }}
          target={target}
          isPending={reschedule.isPending}
          isError={reschedule.isError}
          // Closes only once the write is accepted, and closes the menu with it. The place is
          // validated server-side, so a scheme-less paste is a refusal, and closing regardless
          // would throw away the new date along with the bad link and say nothing at all.
          onConfirm={(input) =>
            reschedule.mutate({ entry, input }, { onSuccess: onClose })
          }
        />
      )}
    </>
  );
}
