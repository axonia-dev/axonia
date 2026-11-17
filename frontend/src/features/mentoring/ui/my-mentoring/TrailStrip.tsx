import { Sparkle } from "lucide-react";
import { useId } from "react";
import { Link } from "react-router-dom";

import type { PairingSummary } from "@/features/mentoring/domain/mentoring";
import { partnerRole } from "@/features/mentoring/ui/journal-people";
import { pairingDates } from "@/features/mentoring/ui/mentoring-dates";
import { nextMeetingLine } from "@/features/mentoring/ui/my-mentoring/meeting-lines";
import { ordinalWord } from "@/features/mentoring/ui/my-mentoring/mentoring-groups";
import {
  stripLabel,
  StripTrail,
} from "@/features/mentoring/ui/my-mentoring/StripTrail";
import { PartnerFace, personName } from "@/features/mentoring/ui/PartnerFace";
import { cn } from "@/lib/cn";
import { dayAndMonth } from "@/lib/declared-on";

interface Props {
  pairing: PairingSummary;
  to: string;
  together: number | null;
  today: string;
  arriving: boolean;
  /** The arrival has played; the strip is an ordinary one again. */
  onArrived: () => void;
}

function reached(pairing: PairingSummary): number {
  return pairing.stops.filter((state) => state === "DONE").length;
}

function aside(pairing: PairingSummary, together: number | null): string {
  if (together !== null && together >= 2) {
    return ` · ${ordinalWord(together)} pairing together`;
  }
  const setOut = reached(pairing) === 0 && pairing.notes === 0;
  return setOut ? " · just set out" : "";
}

function NextMeeting({
  id,
  pairing,
  today,
}: Readonly<{ id: string; pairing: PairingSummary; today: string }>) {
  const line = nextMeetingLine(pairing, today);
  const unset = pairing.nextMeeting === null;
  return (
    <div id={id} className="ml-auto basis-full text-right sm:basis-auto">
      <p
        className={cn(
          "leading-none",
          unset
            ? "text-[13px] font-semibold"
            : "font-display text-[22px] font-bold tracking-tight",
          line.tone === "warning" ? "text-warning-ink" : "text-foreground",
        )}
      >
        {line.big}
      </p>
      <p className="text-muted-foreground mt-1.5 text-[11px] leading-none whitespace-nowrap">
        {line.small}
      </p>
    </div>
  );
}

function Footer({ pairing }: Readonly<{ pairing: PairingSummary }>) {
  const total = pairing.stops.length;
  return (
    <div className="text-muted-foreground flex flex-wrap items-center gap-x-4 gap-y-1.5 text-xs">
      {pairing.nowTopic !== null && (
        <span className="text-warning-ink inline-flex max-w-full min-w-0 items-center gap-1.5 rounded-full bg-[color-mix(in_srgb,var(--warning)_16%,transparent)] px-2.5 py-1 font-semibold">
          <Sparkle aria-hidden className="size-3 flex-none" />
          <span className="min-w-0 wrap-anywhere">
            Now · {pairing.nowTopic}
          </span>
        </span>
      )}
      {total === 0 ? (
        <span>No topics yet</span>
      ) : (
        <span>
          <b className="text-foreground font-semibold">
            {reached(pairing)} of {total}
          </b>{" "}
          topics
        </span>
      )}
      <span>
        <b className="text-foreground font-semibold">{pairing.meetings}</b>{" "}
        {pairing.meetings === 1 ? "meeting" : "meetings"}
      </span>
      {pairing.lastMetOn !== null && (
        <span>last note {dayAndMonth(pairing.lastMetOn)}</span>
      )}
    </div>
  );
}

/** An ongoing pairing as a card: who, when the two meet next, and how far their trail has come. */
export function TrailStrip({
  pairing,
  to,
  together,
  today,
  arriving,
  onArrived,
}: Readonly<Props>) {
  const name = personName(pairing.partnerName, pairing.partnerId);
  const role = partnerRole(pairing);
  const id = useId();
  return (
    <li
      data-arriving={arriving || undefined}
      onAnimationEnd={(event) => {
        if (event.target === event.currentTarget) onArrived();
      }}
      className={cn(
        arriving && "motion-safe:animate-[memory-arrive_0.6s_ease-out_both]",
      )}
    >
      <Link
        to={to}
        aria-label={`${name}, ${role}, ${pairing.communityName}`}
        aria-describedby={`${id}-meeting ${id}-trail`}
        className="border-border hover:border-border-strong focus-visible:ring-focus-ring flex flex-col gap-1 rounded-2xl border bg-[linear-gradient(180deg,var(--surface),color-mix(in_srgb,var(--surface)_70%,var(--background)))] px-4 pt-3.5 pb-3 shadow-sm outline-none focus-visible:ring-2 motion-safe:transition-colors"
      >
        <div className="flex flex-wrap items-center gap-x-3 gap-y-2">
          <span
            className={cn(
              "flex flex-none rounded-full ring-2",
              pairing.myRole === "MENTEE" ? "ring-mentor" : "ring-mentee",
            )}
          >
            <PartnerFace
              userId={pairing.partnerId}
              name={pairing.partnerName}
              avatarUrl={pairing.partnerAvatarUrl}
              className="size-10 text-xs"
            />
          </span>
          <div className="min-w-0 flex-1 basis-40">
            <p className="text-foreground text-[15px] font-semibold wrap-anywhere">
              {name}
            </p>
            <p className="text-muted-foreground text-xs wrap-anywhere">
              {pairing.communityName} · {pairingDates(pairing.startedAt, null)}
              {aside(pairing, together)}
            </p>
          </div>
          <NextMeeting id={`${id}-meeting`} pairing={pairing} today={today} />
        </div>
        <span id={`${id}-trail`} className="sr-only">
          {stripLabel(pairing)}
        </span>
        <StripTrail pairing={pairing} />
        <Footer pairing={pairing} />
      </Link>
    </li>
  );
}
