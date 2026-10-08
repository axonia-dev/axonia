import { Dialog } from "@/components/ui/Dialog";
import { useSeriesList } from "@/features/events/api/use-series";
import { SeriesForm } from "@/features/events/ui/SeriesForm";

interface Props {
  slug: string;
  seriesId: number;
  isOpen: boolean;
  onOpenChange: (isOpen: boolean) => void;
}

/**
 * The standing facts of a series, opened from an occurrence of it.
 *
 * `SeriesForm` has carried an edit mode since the series remodel and nothing in the product ever
 * mounted it: every series was write-once, so a cadence typed wrong, a zone picked in haste, or a
 * standing place that moved to another room could only be answered by starting a second series. The
 * reschedule dialog even pointed at an inherited place the organizer had no way to reach.
 *
 * Reached from a chip rather than from a list of its own, because that is where an organizer is
 * when the question comes up, and because a series with no occurrence on the calendar is a series
 * nobody is looking at. It is deliberately the neighbour of Reschedule and deliberately worded
 * against it: one answers "this occurrence", the other "every occurrence from here".
 */
export function EditSeriesDialog({
  slug,
  seriesId,
  isOpen,
  onOpenChange,
}: Readonly<Props>) {
  const list = useSeriesList(slug);
  const series = list.data?.content.find((entry) => entry.id === seriesId);

  return (
    <Dialog
      isOpen={isOpen}
      onOpenChange={onOpenChange}
      title="Edit series"
      size="2xl"
      pinnedFooter
    >
      {({ close }) =>
        series === undefined ? (
          // A form seeded from nothing would open on blank required fields and save them over the
          // series, so the unread case says so and offers no fields at all.
          <p role="alert" className="text-danger text-sm">
            This series could not be read.
          </p>
        ) : (
          <SeriesForm
            slug={slug}
            series={series}
            onSaved={() => {
              close();
              onOpenChange(false);
            }}
          />
        )
      }
    </Dialog>
  );
}
