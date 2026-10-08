import { useState } from "react";

import { FormError } from "@/components/patterns/FormError";
import { Button } from "@/components/ui/Button";
import { DateTimePicker } from "@/components/ui/DateTimePicker";
import { Dialog } from "@/components/ui/Dialog";
import { DialogFooter } from "@/components/ui/DialogFooter";
import type { UpdateSessionInput } from "@/features/events/domain/events-gateway";
import type { MeetingPlace } from "@/features/events/domain/meeting-place";
import {
  draftFromPlace,
  placeFromDraft,
  placeIsEmpty,
} from "@/features/events/domain/meeting-place";
import type { RescheduleTarget } from "@/features/events/domain/reschedule-target";
import {
  governingZone,
  wallClockOf,
} from "@/features/events/domain/reschedule-target";
import {
  instantToZoned,
  type ZonedParts,
  zonedToInstant,
} from "@/features/events/domain/zoned-instant";
import { MeetingPlaceFields } from "@/features/events/ui/MeetingPlaceFields";
import { MemberZoneSpread } from "@/features/events/ui/MemberZoneSpread";
import { cityOf, viewerZone } from "@/lib/zones";

interface Props {
  slug: string;
  isOpen: boolean;
  onOpenChange: (isOpen: boolean) => void;
  target: RescheduleTarget;
  isPending: boolean;
  isError: boolean;
  /** Handed the whole replacement to write. The dialog never writes: it does not know which of
   *  its two callers mounted it, and only that caller knows what closing means. */
  onConfirm: (input: UpdateSessionInput) => void;
}

/**
 * What three empty fields mean for this occurrence, so an empty block reads as inheritance rather
 * than as a missing place. Carried as the block's description and not as a placeholder: a
 * placeholder is not part of the accessible description, so a screen reader announces "Joining
 * details, blank" and drops the only sentence that tells inheritance from absence - and it
 * disappears for sighted readers the moment anything is typed.
 *
 * The rule is all or nothing, which is why the hint names the whole block rather than any one
 * field: an occurrence that fills in a single part is stating where it meets, and the parts it
 * left blank stay blank rather than falling back one by one.
 *
 * A series with no standing place gets no hint at all. There the three fields fall back to nothing,
 * so an empty block already means what it looks like, and a line saying so is one more thing to
 * read for no answer it did not already have.
 */
function inheritedPlaceHint(seriesPlace: MeetingPlace): string | undefined {
  return placeIsEmpty(seriesPlace)
    ? undefined
    : "All three empty means the series' standing place.";
}

/**
 * The one thing the two fields cannot show: what the typed time comes to on the reader's own
 * clock, or - for an occurrence that may have no hour - that leaving the hour out is an answer.
 *
 * Read back through the same conversion the confirm composes with, rather than formatted
 * independently, so the sentence cannot promise an instant other than the one that would be sent.
 * Omitted when both clocks are the same, because then it says nothing the zone chip has not.
 *
 * The day is named whenever the two clocks disagree about which one it is. An offset wide enough
 * to cross midnight is exactly the offset worth stating, and a bare hour said "00:30 in Paris is
 * 15:30 in Los Angeles" while leaving the reader to assume the same afternoon: this line is the
 * only place the offset appears at all.
 */
function viewerEquivalent(
  when: ZonedParts,
  zone: string,
  isTimeRequired: boolean,
): string | undefined {
  if (!isTimeRequired && when.time.trim() === "") {
    return "Leave empty to keep the hour open.";
  }
  const viewer = viewerZone();
  const at = zonedToInstant(when, zone);
  if (viewer === zone || at === null) {
    return undefined;
  }
  const there = instantToZoned(at.toISOString(), viewer);
  const day = there.date === when.date ? "" : ` on ${civilDay(there.date)}`;
  return `${there.time}${day} in ${cityOf(viewer)}.`;
}

/** A civil date named for a reader. Formatted in UTC because the parts already name a day: read
 *  on any other clock the label would slide by the reader's own offset, which is the very thing
 *  the sentence around it is trying to state. */
function civilDay(date: string): string {
  return new Intl.DateTimeFormat("en-GB", {
    weekday: "short",
    day: "numeric",
    month: "short",
    timeZone: "UTC",
  }).format(new Date(`${date}T00:00:00Z`));
}

interface FormProps {
  slug: string;
  target: RescheduleTarget;
  isPending: boolean;
  isError: boolean;
  onConfirm: (input: UpdateSessionInput) => void;
  onCancel: () => void;
}

/**
 * Both drafts are seeded on mount and never resynced, which is what makes an abandoned edit
 * discard: the dialog unmounts its body on close, so reopening reads the target again. Held on
 * the caller instead, a place typed and then cancelled was written by the next confirm - data the
 * organizer had explicitly thrown away.
 */
function RescheduleForm({
  slug,
  target,
  isPending,
  isError,
  onConfirm,
  onCancel,
}: Readonly<FormProps>) {
  const zone = governingZone(target);
  // Inverted deliberately: an occurrence of a series may keep its hour open, and the server
  // composes it at midnight in the series' zone. A session with no series states its own zone and
  // is refused without a time, so there the hour is not optional.
  const isTimeRequired = target.seriesTimeZone === null;
  const [when, setWhen] = useState<ZonedParts>(() => ({
    date: instantToZoned(target.scheduledAt, zone).date,
    time: wallClockOf(target) ?? "",
  }));
  const [place, setPlace] = useState(() =>
    draftFromPlace(target.placeOverride),
  );
  const description = viewerEquivalent(when, zone, isTimeRequired);
  const placeHint = inheritedPlaceHint(target.seriesPlace);
  const incomplete =
    when.date.trim() === "" || (isTimeRequired && when.time.trim() === "");

  return (
    <div className="flex flex-col gap-4">
      <DateTimePicker
        label="New date and time"
        value={when}
        onChange={setWhen}
        timeZone={zone}
        isTimeRequired={isTimeRequired}
        {...(description === undefined ? {} : { description })}
      />
      <MemberZoneSpread
        slug={slug}
        when={when}
        zone={zone}
        durationMinutes={target.durationMinutes}
      />
      <MeetingPlaceFields
        value={place}
        onChange={setPlace}
        {...(placeHint === undefined ? {} : { description: placeHint })}
      />
      <FormError error={isError} />
      <DialogFooter>
        <Button size="sm" variant="ghost" onPress={onCancel}>
          Cancel
        </Button>
        <Button
          size="sm"
          isDisabled={isPending || incomplete}
          onPress={() =>
            onConfirm({
              scheduledAt: {
                date: when.date,
                time: when.time.trim() === "" ? null : when.time,
                zone,
              },
              durationMinutes: target.durationMinutes,
              facilitatorUserId: target.facilitatorUserId,
              place: placeFromDraft(place),
            })
          }
        >
          Confirm
        </Button>
      </DialogFooter>
    </div>
  );
}

/**
 * The one editor for when and where an occurrence meets, mounted by the session page and by the
 * calendar alike. Two of them is how the same schedule came to be read on one clock and written
 * on another: a dialog written against a `Session` could only ever serve the page that had one,
 * so the calendar grew its own, and the two drifted.
 *
 * The whole replacement is handed to the caller rather than written here, because
 * `PUT /sessions/{id}` replaces every field it names and only the caller knows what it holds
 * beyond what this dialog edits.
 *
 * Closing is the caller's signal that the write landed: it calls `onOpenChange(false)` from its
 * own `onSuccess`, and nowhere else. The place is validated server-side, so an ordinary paste - a
 * join link with no scheme - is a refusal, and closing on it would throw away the new date along
 * with the bad link and say nothing at all.
 */
export function RescheduleDialog({
  slug,
  isOpen,
  onOpenChange,
  target,
  isPending,
  isError,
  onConfirm,
}: Readonly<Props>) {
  return (
    <Dialog
      isOpen={isOpen}
      onOpenChange={onOpenChange}
      title="Reschedule session"
    >
      {({ close }) => (
        <RescheduleForm
          slug={slug}
          target={target}
          isPending={isPending}
          isError={isError}
          onConfirm={onConfirm}
          onCancel={close}
        />
      )}
    </Dialog>
  );
}
