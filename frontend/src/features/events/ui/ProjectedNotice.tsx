import { cn } from "@/lib/cn";

/** What a ghost occurrence is: read off the series' cadence rather than opened as a session of
 *  its own, so it is never mistaken for one. Shared by every place a schedule entry is shown,
 *  so the wording only ever lives in one place. */
export function ProjectedNotice({
  className,
}: Readonly<{ className?: string }>) {
  return (
    <p className={cn("text-muted-foreground text-sm", className)}>
      From the series cadence, not opened yet
    </p>
  );
}
