import { Minus, Plus } from "lucide-react";
import { useId, useRef } from "react";

import { IconButton } from "@/components/ui/IconButton";

const MAX_LIMIT = 10;

/** One to ten, then no limit: a step past ten lifts the limit, a step back from none is ten. */
function stepLimit(limit: number | null, by: 1 | -1): number | null {
  if (limit === null) return by === -1 ? MAX_LIMIT : null;
  const next = limit + by;
  return next > MAX_LIMIT ? null : Math.max(1, next);
}

/** How many mentees at once, stepped without a Save button. */
export function LimitStepper({
  limit,
  isDisabled,
  onChange,
}: Readonly<{
  limit: number | null;
  isDisabled: boolean;
  onChange: (limit: number | null) => void;
}>) {
  const labelId = useId();
  const group = useRef<HTMLDivElement>(null);
  /** A button reaching its bound is disabled and would drop focus, so focus crosses to the other. */
  function step(by: 1 | -1) {
    const next = stepLimit(limit, by);
    const bound = by === -1 ? next === 1 : next === null;
    if (bound) {
      group.current
        ?.querySelector<HTMLElement>(
          `[aria-label="${by === -1 ? "More mentees" : "Fewer mentees"}"]`,
        )
        ?.focus();
    }
    onChange(next);
  }
  return (
    <div className="mt-3 flex flex-wrap items-center gap-3">
      <span id={labelId} className="text-muted-foreground text-sm">
        Most mentees at once
      </span>
      <div
        ref={group}
        role="group"
        aria-labelledby={labelId}
        className="bg-surface-sunken inline-flex items-center rounded-xl"
      >
        <IconButton
          label="Fewer mentees"
          isDisabled={isDisabled || limit === 1}
          onPress={() => step(-1)}
        >
          <Minus aria-hidden />
        </IconButton>
        <output
          aria-live="polite"
          className="text-foreground min-w-24 text-center text-sm font-semibold tabular-nums"
        >
          {limit === null ? "No limit" : `At most ${limit}`}
        </output>
        <IconButton
          label="More mentees"
          isDisabled={isDisabled || limit === null}
          onPress={() => step(1)}
        >
          <Plus aria-hidden />
        </IconButton>
      </div>
    </div>
  );
}
