import type { ReactNode } from "react";

import type { Chapter } from "@/features/health/domain/finding-routing";
import { findingsFor } from "@/features/health/domain/finding-routing";
import type { HealthSnapshot } from "@/features/health/domain/health";
import type { HealthStatus } from "@/features/health/domain/status";
import { FindingList } from "@/features/health/ui/FindingList";
import { StatusPill } from "@/features/health/ui/StatusPill";
import { cn } from "@/lib/cn";

/**
 * One question, its status, its evidence and then its findings. A finding renders only in the
 * panel its evidence is drawn in, which is what keeps it stated exactly once on the page.
 */
export function Panel({
  id,
  title,
  status,
  snapshot,
  children,
}: Readonly<{
  id: Chapter;
  title: string;
  status: HealthStatus;
  snapshot: HealthSnapshot;
  children: ReactNode;
}>) {
  const headingId = `${id}-heading`;
  return (
    <section
      id={id}
      aria-labelledby={headingId}
      className="border-border bg-surface flex min-w-0 scroll-mt-20 flex-col rounded-lg border px-[22px] py-5"
    >
      <header className="flex items-center justify-between gap-3">
        <h2
          id={headingId}
          className="text-muted-foreground text-[11px] font-semibold tracking-[0.08em] uppercase"
        >
          {title}
        </h2>
        <StatusPill status={status} />
      </header>
      {children}
      <FindingList
        findings={findingsFor(id, snapshot.diagnosis.findings)}
        snapshot={snapshot}
      />
    </section>
  );
}

export function Facts({ children }: Readonly<{ children: ReactNode }>) {
  return <div className="mt-4 flex flex-wrap gap-x-7 gap-y-3">{children}</div>;
}

/**
 * A number over its caption. The spaces between the parts are for anything reading the text in
 * one run, a screen reader included; the flex rows never render them.
 */
export function Fact({
  value,
  caption,
  trend,
  small = false,
  muted = false,
  children,
}: Readonly<{
  value: ReactNode;
  caption: string;
  trend?: ReactNode;
  small?: boolean;
  muted?: boolean;
  children?: ReactNode;
}>) {
  return (
    <div className="min-w-0">
      <div className="flex flex-wrap items-baseline gap-x-2">
        <span
          className={cn(
            "font-display leading-none font-semibold",
            small ? "text-xl" : "text-[28px]",
            muted ? "text-muted-foreground" : "text-foreground",
          )}
        >
          {value}
        </span>{" "}
        {trend}
      </div>{" "}
      {children}
      <p className="text-muted-foreground mt-1 text-xs">{caption}</p>
    </div>
  );
}

export function SubLabel({
  start,
  end,
}: Readonly<{ start: string; end: string }>) {
  return (
    <div className="text-muted-foreground mt-4 flex justify-between gap-3 text-xs">
      <span>{start}</span>
      <span>{end}</span>
    </div>
  );
}
