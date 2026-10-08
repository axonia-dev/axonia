import { AnimatedCount } from "@/components/ui/AnimatedCount";
import { cn } from "@/lib/cn";

interface CountBadgeProps {
  value: number;
  /** Names what is being counted for a screen reader, e.g. "placements". Sighted readers take the
   *  unit from the row the badge sits in; a reader hearing the row aloud gets only the bare number. */
  label?: string;
  className?: string;
}

/** A small rounded-full pill for a numeric count; animates when the value changes. */
export function CountBadge({
  value,
  label,
  className,
}: Readonly<CountBadgeProps>) {
  return (
    <span
      className={cn(
        "border-border bg-surface text-muted-foreground inline-flex items-center rounded-full border px-2 py-0.5 text-xs font-semibold",
        className,
      )}
    >
      <AnimatedCount value={value} />
      {label !== undefined && <span className="sr-only"> {label}</span>}
    </span>
  );
}
