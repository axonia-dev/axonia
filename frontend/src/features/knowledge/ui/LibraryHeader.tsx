import { AnimatedCount } from "@/components/ui/AnimatedCount";
import { useLibraryStats } from "@/features/knowledge/api/use-library-insights";
import type { LibraryStats } from "@/features/knowledge/domain/library-stats";
import { CaptureArtifactButton } from "@/features/knowledge/ui/CaptureArtifactButton";
import { pluralWord } from "@/lib/plural";

function VitalityLine({
  stats,
}: Readonly<{ stats: LibraryStats | undefined }>) {
  if (!stats) {
    return (
      <p className="text-muted-foreground text-sm">
        Taking stock of the shelves…
      </p>
    );
  }
  if (stats.total === 0) {
    return (
      <p className="text-muted-foreground text-sm">
        An empty shelf, ready for its first artifact.
      </p>
    );
  }
  const count = "text-primary font-semibold";
  return (
    <p className="text-muted-foreground text-sm">
      <AnimatedCount value={stats.total} className={count} />{" "}
      {pluralWord(stats.total, "artifact")} from{" "}
      <AnimatedCount value={stats.sessionCount} className={count} />{" "}
      {pluralWord(stats.sessionCount, "session")} ·{" "}
      <AnimatedCount value={stats.capturedThisMonth} className={count} /> this
      month
    </p>
  );
}

export function LibraryHeader({ slug }: Readonly<{ slug: string }>) {
  const stats = useLibraryStats(slug);

  return (
    <header className="flex flex-wrap items-start justify-between gap-3">
      <div className="flex flex-col gap-1">
        <h2 className="font-display text-foreground text-2xl font-semibold">
          Library
        </h2>
        <VitalityLine stats={stats.data} />
      </div>
      <CaptureArtifactButton slug={slug} />
    </header>
  );
}
