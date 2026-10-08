import { cn } from "@/lib/cn";

interface Props {
  label: string;
  overrunMinutes: number;
}

/**
 * The one place a session's time budget is drawn.
 *
 * It replaces the session-level meter, which used to sit above the first section and read as that
 * section's own. Being a line across the sheet rather than a bar with a percentage, it cannot
 * clamp: a session that ran twice its length simply has more sheet below the line.
 */
export function PlannedEndMarker({ label, overrunMinutes }: Readonly<Props>) {
  const over = overrunMinutes > 0;
  return (
    <li className="col-span-full my-1 flex items-center gap-3 max-sm:gap-2">
      <span className="text-muted-foreground w-13 shrink-0 text-right text-xs tabular-nums max-sm:w-10">
        {label}
      </span>
      <span
        aria-hidden
        className={cn(
          "flex-1 border-t border-dashed",
          over ? "border-warning/55" : "border-border-strong",
        )}
      />
      <span
        className={cn(
          "shrink-0 text-[10px] font-bold tracking-widest uppercase",
          over ? "text-warning" : "text-muted-foreground",
        )}
      >
        planned end{over && ` · ran ${overrunMinutes} min over`}
      </span>
    </li>
  );
}
