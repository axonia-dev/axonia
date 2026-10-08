import { useNavigate } from "react-router-dom";

import { Button } from "@/components/ui/Button";
import { useIsOrganizer } from "@/features/community/api/use-organizer";
import { useCommunityCrumbs } from "@/features/community/ui/community-chrome";
import { useOpenOccurrence } from "@/features/events/api/use-open-occurrence";
import { useSchedule } from "@/features/events/api/use-schedule";
import { useSeriesList } from "@/features/events/api/use-series";
import { useSkipOccurrence } from "@/features/events/api/use-sessions";
import type { ScheduleEntry } from "@/features/events/domain/schedule";
import { hasPassed } from "@/features/events/domain/schedule";
import { addDaysIso } from "@/features/events/domain/schedule-window";
import { pageItems } from "@/features/events/ui/page-items";

function sectionHeading(section: ScheduleEntry["sections"][number]): string {
  const sectionType = section.sectionType ?? "Section";
  return section.title ? `${sectionType} · ${section.title}` : sectionType;
}

/**
 * Skipping is for calling an occurrence off in advance. The server refuses a retroactive one
 * outright (`OCCURRENCE_IN_PAST`), and this page surfaces no refusal at all, so past the hour the
 * button read as live and answered by doing nothing.
 */
function skippable(entry: ScheduleEntry, canManage: boolean): boolean {
  return canManage && !hasPassed(entry);
}

interface Props {
  slug: string;
  seriesId: number;
  date: string;
}

/**
 * A read-only preview of a projected (virtual) occurrence. Viewing never
 * materializes a row — only the explicit "Open for sign-up" action does, after
 * which the member is redirected to the now-real session page.
 */
export function OccurrencePreviewPage({
  slug,
  seriesId,
  date,
}: Readonly<Props>) {
  const fromIso = `${date}T00:00:00.000Z`;
  const toIso = addDaysIso(fromIso, 1);
  const scheduleQuery = useSchedule(slug, fromIso, toIso);
  const seriesList = pageItems(useSeriesList(slug));
  const { open, isPending } = useOpenOccurrence(slug);
  const navigate = useNavigate();
  const canManage = useIsOrganizer(slug);
  const skip = useSkipOccurrence(slug);

  const entry =
    (scheduleQuery.data ?? []).find(
      (e) => e.virtual && e.seriesId === seriesId && e.occurrenceDate === date,
    ) ?? null;
  const seriesTitle =
    seriesList.find((s) => s.id === seriesId)?.title ?? "Session";
  const label = entry?.title ?? seriesTitle;
  useCommunityCrumbs([{ label }]);

  if (scheduleQuery.isLoading) {
    return <p className="text-muted-foreground text-sm">Loading…</p>;
  }
  if (!entry) {
    return (
      <p className="text-muted-foreground text-sm">
        This occurrence is no longer on the schedule.
      </p>
    );
  }

  return (
    <section className="flex flex-col gap-6">
      <div className="flex items-center justify-between gap-2">
        <h1 className="text-foreground font-display text-xl font-semibold">
          {label}
        </h1>
        <span className="border-border text-muted-foreground rounded border border-dashed px-2 py-0.5 text-xs">
          Projected · open
        </span>
      </div>
      <div className="flex flex-col gap-3">
        {entry.sections.map((section, idx) => (
          <div
            key={section.id ?? idx}
            className="border-border-strong bg-surface rounded border p-4"
          >
            <h2 className="text-foreground text-sm font-semibold">
              {sectionHeading(section)}
            </h2>
            <p className="text-muted-foreground mt-1 text-sm">
              Open for sign-up
            </p>
          </div>
        ))}
      </div>
      <div className="flex items-center gap-2">
        <Button
          className="self-start"
          isDisabled={isPending}
          onPress={() => open(seriesId, date)}
        >
          Open for sign-up
        </Button>
        {skippable(entry, canManage) && (
          <Button
            variant="ghost"
            isDisabled={skip.isPending}
            onPress={() =>
              skip.mutate(
                { seriesId, occurrenceDate: date },
                {
                  onSuccess: () =>
                    void navigate(`/communities/${slug}/schedule`),
                },
              )
            }
          >
            Skip
          </Button>
        )}
      </div>
    </section>
  );
}
