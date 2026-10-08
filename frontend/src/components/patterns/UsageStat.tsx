import type { ReactNode } from "react";

import { AnimatedCount } from "@/components/ui/AnimatedCount";

interface UsageStatProps {
  count: number;
  /** Noun for a single dependent, e.g. "community" / "artifact". */
  singular: string;
  /** Noun for many dependents, e.g. "communities" / "artifacts". */
  plural: string;
  /** A small leading icon (rendered decoratively). */
  icon: ReactNode;
}

/** A friendly "Used by N …" readout; the count pops when it changes. */
export function UsageStat({
  count,
  singular,
  plural,
  icon,
}: Readonly<UsageStatProps>) {
  if (count === 0) {
    return (
      <p className="text-muted-foreground text-sm">
        Not used by any {singular} yet.
      </p>
    );
  }
  return (
    <p className="text-muted-foreground flex items-center gap-1.5 text-sm">
      <span aria-hidden="true" className="inline-flex">
        {icon}
      </span>
      Used by{" "}
      <AnimatedCount value={count} className="text-foreground font-semibold" />{" "}
      {count === 1 ? singular : plural}.
    </p>
  );
}
