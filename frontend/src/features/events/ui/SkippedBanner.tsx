import { Button } from "@/components/ui/Button";
import { useUnskipOccurrence } from "@/features/events/api/use-sessions";
import type { Session } from "@/features/events/domain/session";

interface Props {
  slug: string;
  session: Session;
  canManage: boolean;
}

/** Marks a skipped occurrence and lets a manager restore (unskip) it. */
export function SkippedBanner({ slug, session, canManage }: Readonly<Props>) {
  const unskip = useUnskipOccurrence(slug);
  const canRestore =
    canManage && session.seriesId !== null && session.occurrenceDate !== null;

  return (
    <div className="border-border-strong bg-surface-sunken flex items-center justify-between gap-3 rounded border border-dashed p-3">
      <p className="text-muted-foreground text-sm font-medium">
        This occurrence has been skipped.
      </p>
      {canRestore && (
        <Button
          variant="ghost"
          size="sm"
          isDisabled={unskip.isPending}
          onPress={() =>
            unskip.mutate({
              seriesId: session.seriesId as number,
              occurrenceDate: session.occurrenceDate as string,
            })
          }
        >
          Restore
        </Button>
      )}
    </div>
  );
}
