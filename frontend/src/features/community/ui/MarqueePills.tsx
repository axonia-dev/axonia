import { CirclePlay, Library, ListChecks } from "lucide-react";
import type { ReactNode } from "react";

import type { SessionRecap } from "@/features/community/domain/session-recap";
import { plural } from "@/lib/plural";

function Pill({
  children,
  tone = "plain",
}: Readonly<{ children: ReactNode; tone?: "plain" | "mine" }>) {
  return (
    <span
      className={
        tone === "mine"
          ? "text-warning inline-flex items-center gap-1.5 rounded-full border border-[color-mix(in_srgb,var(--warning)_35%,var(--surface))] bg-[color-mix(in_srgb,var(--warning)_14%,var(--surface))] px-3 py-1 text-xs font-semibold"
          : "border-border-strong bg-surface-sunken text-foreground inline-flex items-center gap-1.5 rounded-full border px-3 py-1 text-xs font-semibold"
      }
    >
      {children}
    </span>
  );
}

/** The facts under the headline, each dropped rather than faked when the session carries none. */
export function factsOf(recap: SessionRecap): string {
  return [
    recap.talks.length > 0 ? plural(recap.talks.length, "talk") : null,
    recap.totalMinutes > 0 ? `${recap.totalMinutes} min` : null,
  ]
    .filter((part) => part !== null)
    .join(" · ");
}

/**
 * An action point of the reader's own is named ahead of the tally: it is the one thing here that
 * is owed rather than merely reported.
 */
function ActionPointPill({ recap }: Readonly<{ recap: SessionRecap }>) {
  if (recap.mine !== null) {
    return (
      <Pill tone="mine">
        <ListChecks size={13} />1 action point assigned to me
      </Pill>
    );
  }
  if (recap.openActionPoints === 0) {
    return null;
  }
  return (
    <Pill>
      <ListChecks size={13} />
      {plural(recap.openActionPoints, "action point")} open
    </Pill>
  );
}

interface Props {
  recap: SessionRecap;
}

/**
 * What the session left behind.
 *
 * Each pill is dropped rather than greyed out when the session carries nothing for it: a disabled
 * "Watch the recording" is a promise the page cannot keep. Nothing here writes: the overview
 * reports a session's record, and the session's own page is where it is written.
 */
export function RecapPills({ recap }: Readonly<Props>) {
  return (
    <div className="mt-4 flex flex-wrap items-center gap-2">
      {recap.recordingUrl !== null && (
        <a
          href={recap.recordingUrl}
          target="_blank"
          rel="noreferrer"
          className="bg-primary text-primary-foreground hover:bg-primary-strong inline-flex items-center gap-1.5 rounded-full px-3 py-1 text-xs font-semibold transition-colors"
        >
          <CirclePlay size={13} />
          Watch the recording
        </a>
      )}
      {recap.resourceCount > 0 && (
        <Pill>
          <Library size={13} />
          {plural(recap.resourceCount, "resource")}
        </Pill>
      )}
      <ActionPointPill recap={recap} />
    </div>
  );
}
