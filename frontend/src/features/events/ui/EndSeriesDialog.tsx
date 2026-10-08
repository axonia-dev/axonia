import { FormError } from "@/components/patterns/FormError";
import { Button } from "@/components/ui/Button";
import { Dialog } from "@/components/ui/Dialog";
import { DialogFooter } from "@/components/ui/DialogFooter";
import { useEndSeries } from "@/features/events/api/use-series";

interface Props {
  slug: string;
  seriesId: number;
  isOpen: boolean;
  onOpenChange: (isOpen: boolean) => void;
}

/**
 * Ending a series, which is the answer a single occurrence never was.
 *
 * Calling one session off and stopping a recurring commitment are different decisions, and the
 * calendar only ever offered the first: an organizer who wanted the series to stop had to skip or
 * cancel each occurrence as it appeared, forever. The backend has carried the transition since the
 * series remodel and nothing reached it.
 *
 * Confirmed rather than immediate, and worded around what actually happens: `ScheduleService`
 * projects ghost occurrences only for an ACTIVE series, so ending one stops every future
 * occurrence at once while the sessions already materialised keep their place on the calendar.
 */
export function EndSeriesDialog({
  slug,
  seriesId,
  isOpen,
  onOpenChange,
}: Readonly<Props>) {
  const end = useEndSeries(slug);

  return (
    <Dialog
      isOpen={isOpen}
      onOpenChange={onOpenChange}
      title="End this series?"
      role="alertdialog"
    >
      {() => (
        <div className="flex flex-col gap-4">
          <p className="text-muted-foreground text-sm">
            No further occurrence is scheduled. The sessions already on the
            calendar stay where they are.
          </p>
          <FormError error={end.isError} />
          <DialogFooter>
            <Button
              size="sm"
              variant="ghost"
              isDisabled={end.isPending}
              onPress={() => onOpenChange(false)}
            >
              Cancel
            </Button>
            <Button
              size="sm"
              variant="danger"
              isDisabled={end.isPending}
              // Closes only once the transition is accepted: closing regardless would leave a
              // series still producing occurrences under an organizer who was told it stopped.
              onPress={() =>
                end.mutate(seriesId, { onSuccess: () => onOpenChange(false) })
              }
            >
              End series
            </Button>
          </DialogFooter>
        </div>
      )}
    </Dialog>
  );
}
