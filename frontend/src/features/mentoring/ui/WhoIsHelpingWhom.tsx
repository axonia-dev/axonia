import { ArrowRight } from "lucide-react";

import type {
  MentoringRole,
  ShownPairing,
} from "@/features/mentoring/domain/mentoring";
import { PartnerFace, personName } from "@/features/mentoring/ui/PartnerFace";
import { cn } from "@/lib/cn";
import { monthName } from "@/lib/declared-on";
import { useNow } from "@/lib/use-wake-at";

const ROLE = {
  MENTOR: { label: "Mentor", ink: "text-mentor" },
  MENTEE: { label: "Mentee", ink: "text-mentee-ink" },
} as const;

function Side({
  as: role,
  person,
}: Readonly<{ as: MentoringRole; person: ShownPairing["mentor"] }>) {
  const mentee = role === "MENTEE";
  return (
    <div
      className={cn(
        "flex min-w-0 items-center gap-2 sm:flex-1",
        mentee && "sm:flex-row-reverse sm:text-right",
      )}
    >
      <PartnerFace
        userId={person.userId}
        name={person.displayName}
        avatarUrl={person.avatarUrl}
      />
      <div className="flex min-w-0 flex-col">
        <span
          className={cn(
            "text-[11px] font-semibold tracking-[0.04em] uppercase",
            ROLE[role].ink,
          )}
        >
          {ROLE[role].label}
        </span>
        <span className="text-foreground text-sm font-semibold wrap-anywhere">
          {personName(person.displayName, person.userId)}
        </span>
      </div>
    </div>
  );
}

/** Stacked on a phone, so each name has the row's width; side by side from `sm` up. */
function Row({ pairing, now }: Readonly<{ pairing: ShownPairing; now: Date }>) {
  return (
    <li className="border-border bg-surface flex flex-col gap-1.5 rounded-2xl border px-3.5 py-2.5 sm:flex-row sm:items-center sm:gap-4">
      <Side as="MENTOR" person={pairing.mentor} />
      <div className="text-muted-foreground flex flex-none items-center gap-1.5 pl-2.5 sm:flex-col sm:gap-0 sm:pl-0">
        <ArrowRight aria-hidden className="size-4 rotate-90 sm:rotate-0" />
        <span className="text-[11px] whitespace-nowrap">
          since {monthName(pairing.startedOn, now)}
        </span>
      </div>
      <Side as="MENTEE" person={pairing.mentee} />
    </li>
  );
}

/** Every ongoing pairing of the community its two people let show, in the server's neutral order. */
export function WhoIsHelpingWhom({
  pairings,
}: Readonly<{ pairings: ShownPairing[] }>) {
  const now = useNow();
  if (pairings.length === 0) return null;
  return (
    <section className="flex min-w-0 flex-col gap-3">
      <h2 className="font-display text-foreground text-lg font-semibold">
        Who’s helping whom
      </h2>
      <ul className="flex flex-col gap-2">
        {pairings.map((pairing) => (
          <Row key={pairing.id} pairing={pairing} now={now} />
        ))}
      </ul>
    </section>
  );
}
