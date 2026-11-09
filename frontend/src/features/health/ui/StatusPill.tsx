import type {
  HealthStatus,
  StatusLevel,
} from "@/features/health/domain/status";
import { STATUS_WORD } from "@/features/health/domain/status";
import { cn } from "@/lib/cn";

const LEVEL_CLASS: Record<StatusLevel, string> = {
  WORRY: "bg-danger/15 text-danger-ink",
  WATCH: "bg-warning/15 text-warning-ink",
  GOOD: "bg-success/15 text-success-ink",
  GREAT: "bg-great text-success-foreground",
  QUIET: "bg-surface-sunken text-quiet-ink",
};

/** The only place, with finding icons, that colour judges; the word always rides along. */
export function StatusPill({
  status,
  label = STATUS_WORD[status.code],
}: Readonly<{ status: HealthStatus; label?: string }>) {
  return (
    <span
      data-level={status.level}
      className={cn(
        "inline-flex shrink-0 items-center gap-1.5 rounded-full px-2 py-1 text-[11px] font-semibold whitespace-nowrap sm:px-2.5 sm:text-xs",
        LEVEL_CLASS[status.level],
      )}
    >
      <span
        aria-hidden
        className="size-[7px] shrink-0 rounded-full bg-current"
      />
      {label}
    </span>
  );
}
