import { useId } from "react";

import { Button } from "@/components/ui/Button";
import { DateTimePicker } from "@/components/ui/DateTimePicker";
import { Dialog } from "@/components/ui/Dialog";
import { DialogFooter } from "@/components/ui/DialogFooter";
import {
  type ZonedParts,
  zonedToInstant,
} from "@/features/events/domain/zoned-instant";
import { formatReleaseInstant } from "@/features/events/ui/release-copy";
import { viewerZone } from "@/lib/zones";

interface Props {
  title: string;
  fieldLabel: string;
  isOpen: boolean;
  onOpenChange: (open: boolean) => void;
  value: ZonedParts;
  onValueChange: (value: ZonedParts) => void;
  hasCurrentSchedule: boolean;
  onConfirm: (releaseAt: string) => void;
  isPending: boolean;
  /** Latest instant the picker admits, as an ISO instant. Absent for a message with no
   *  upper bound, which is how the wrap-up works. */
  latestReleaseAt?: string;
}

/** The prompt says what the picker will do, and - where the message type has one - what
 *  bounds it, naming the session's own start rather than leaving the bound to be discovered by a
 *  refused confirm. */
function prompt(
  hasCurrentSchedule: boolean,
  latestReleaseAt: string | undefined,
): string {
  const sentences = ["Pick when it should send."];
  if (hasCurrentSchedule) {
    sentences.push("This replaces the current schedule.");
  }
  if (latestReleaseAt !== undefined) {
    sentences.push(
      `It has to land before the session starts, ${formatReleaseInstant(latestReleaseAt)}.`,
    );
  }
  return sentences.join(" ");
}

/**
 * The instant `value` names, or null when it names none this dialog can send: half-typed, or
 * unreadable.
 *
 * A release is an instant a person picks for themselves, so the parts are read on the viewer's
 * own clock: no community zone governs when a mail goes out.
 *
 * `value` is the caller's state rather than the field's, and the parse happens on every render
 * rather than only inside the confirm handler, so an unreadable one has to answer null rather
 * than throw - an exception on a render path blanks the page instead of surfacing an error.
 * `zonedToInstant` answers null by contract, which is why the try/catch that used to contain a
 * throw is gone rather than reproduced.
 *
 * Parsing exactly once is what keeps Confirm honest: the button is disabled on the same null
 * that stops the bound being checked, so no value can be unreadable enough to skip the check
 * and readable enough to send.
 */
function pickedInstant(value: ZonedParts): string | null {
  if (value.date.trim() === "" || value.time.trim() === "") {
    return null;
  }
  return zonedToInstant(value, viewerZone())?.toISOString() ?? null;
}

/**
 * The reminder's pre-submit check, and now the whole of it: the bound is on a date and a time
 * together, and neither field can express half of it, so there is nothing for the fields
 * themselves to refuse. The prompt names the bound, this refuses anything past it, and the two
 * ways an organizer sets a value are held to the same rule.
 *
 * Only the upper bound is checked here. The lower one is `now`, which advances while the
 * dialog sits open, so it stays the server's refusal to give rather than a client-side timer
 * that disables a button under the organizer's cursor.
 */
function violatedBound(
  picked: string | null,
  latestReleaseAt: string | undefined,
): string | null {
  if (picked === null || latestReleaseAt === undefined) {
    return null;
  }
  return new Date(picked) >= new Date(latestReleaseAt) ? latestReleaseAt : null;
}

/**
 * "Send it Friday": any instant, not just now or never.
 *
 * Which instants are admissible is the message type's own business, expressed as
 * `latestReleaseAt` plus the server's refusal, never as a rule this dialog holds. The wrap-up
 * allows a past pick because `SessionReleaseService.setWrapUpRelease` clamps a past `releaseAt` up
 * to the server's own clock, so it is always still claimable by the next produce tick. The
 * reminder is bounded above by the session's own start and its server
 * refuses anything outside that window; `latestReleaseAt` is a courtesy, not the guard,
 * because `now` advances while the dialog is open and a pick that was in range when made can
 * still be refused on confirm.
 *
 * `value` is owned by `ReleaseChangeMenu`, not seeded here: this dialog is never unmounted
 * while the menu's popover exists (its `isOpen` prop just toggles visibility), so a local
 * `useState` initializer would only ever run once and go stale after the first "Send it now" /
 * "Do not send it" / cancelled edit. The caller reseeds `value` from the live session in the
 * same handler that opens the dialog.
 */
export function ReleaseScheduleDialog({
  title,
  fieldLabel,
  isOpen,
  onOpenChange,
  value,
  onValueChange,
  hasCurrentSchedule,
  onConfirm,
  isPending,
  latestReleaseAt,
}: Readonly<Props>) {
  const picked = pickedInstant(value);
  const tooLate = violatedBound(picked, latestReleaseAt);
  const errorId = useId();

  return (
    <Dialog isOpen={isOpen} onOpenChange={onOpenChange} title={title}>
      <div className="flex flex-col gap-4">
        <p className="text-muted-foreground text-sm">
          {prompt(hasCurrentSchedule, latestReleaseAt)}
        </p>
        {/* The refusal is about the pair, not about either field, so the reason hangs off a group
            named for the whole control: a reader tabbing back to it hears why it was refused
            rather than only the alert that flashed past as it appeared. The invalid state itself
            is on the segments, where ARIA admits it, and on their border. */}
        <div
          role="group"
          aria-label={fieldLabel}
          {...(tooLate === null ? {} : { "aria-describedby": errorId })}
        >
          {/* Short enough to leave the group's own name - which says which message this is -
              carrying the detail, and to keep the two field names it composes readable. */}
          <DateTimePicker
            label="Sends at"
            value={value}
            onChange={onValueChange}
            timeZone={viewerZone()}
            isTimeRequired
            isInvalid={tooLate !== null}
          />
        </div>
        {tooLate !== null && (
          <p id={errorId} role="alert" className="text-danger text-sm">
            That is after the session starts. Pick a time before{" "}
            {formatReleaseInstant(tooLate)}.
          </p>
        )}
        <DialogFooter>
          <Button variant="ghost" onPress={() => onOpenChange(false)}>
            Cancel
          </Button>
          <Button
            isDisabled={isPending || picked === null || tooLate !== null}
            onPress={() => {
              if (picked !== null) onConfirm(picked);
            }}
          >
            Confirm
          </Button>
        </DialogFooter>
      </div>
    </Dialog>
  );
}
