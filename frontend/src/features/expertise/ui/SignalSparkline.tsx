import type { Evidence } from "@/features/expertise/domain/expertise";
import { TIER_META } from "@/features/expertise/ui/tier-meta";
import { cn } from "@/lib/cn";

const CAP = 5;

interface Bar {
  className: string;
  height: number;
}

/** A bar per signal, in the colour of the tier that kind of signal earns, so it reads with the row's rail.
 *  Offers take the full muted foreground: the Interested dot is too pale for a 4px bar. */
function barsFor(evidence: Evidence): Bar[] {
  const bars: Bar[] = [];
  const push = (count: number, className: string, height: number) => {
    for (let i = 0; i < Math.min(count, CAP); i += 1) {
      bars.push({ className, height });
    }
  };
  push(evidence.presentedCount, TIER_META.PRACTITIONER.dotClassName, 16);
  push(evidence.contributedCount, TIER_META.CONTRIBUTOR.dotClassName, 10);
  push(evidence.offeredCount, "bg-muted-foreground", 6);
  return bars;
}

interface Props {
  evidence: Evidence;
}

/** Drawn from two signals up: a single bar shows nothing the row's summary does not already say. */
export function SignalSparkline({ evidence }: Readonly<Props>) {
  const bars = barsFor(evidence);
  if (bars.length < 2) return null;

  const label = `${evidence.presentedCount} presented, ${evidence.contributedCount} contributed, ${evidence.offeredCount} offered`;

  return (
    <span
      role="img"
      aria-label={label}
      className="flex h-4 shrink-0 items-end gap-[3px]"
    >
      {bars.map((bar, idx) => (
        <span
          key={idx}
          aria-hidden
          className={cn("w-1 rounded-full", bar.className)}
          style={{ height: `${bar.height}px` }}
        />
      ))}
    </span>
  );
}
