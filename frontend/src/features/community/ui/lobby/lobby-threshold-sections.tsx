import { CalendarDays, FileText, MessageSquare, Mic } from "lucide-react";
import type { ReactNode } from "react";

import { MemberFacepile } from "@/components/patterns/MemberFacepile";
import type { LobbyItem } from "@/features/community/domain/lobby";
import {
  NOTHING_SCHEDULED,
  sessionName,
  sessionWhen,
} from "@/features/community/ui/lobby/lobby-copy";
import { LiveDot } from "@/features/community/ui/lobby/LobbyCard";
import type { LobbyFaces } from "@/features/community/ui/lobby/use-lobby-faces";
import { pluralWord } from "@/lib/plural";

const CROWD = 12;

const LABEL =
  "text-muted-foreground text-xs font-bold tracking-widest uppercase";

export function Stat({
  value,
  noun,
  verb = "",
}: Readonly<{ value: number; noun: string; verb?: string }>) {
  return (
    <div className="text-muted-foreground flex flex-col text-xs">
      <span className="font-display text-foreground text-2xl font-bold tabular-nums">
        {value}
      </span>
      {verb === ""
        ? pluralWord(value, noun)
        : `${pluralWord(value, noun)} ${verb}`}
    </div>
  );
}

function LatelyLine({
  icon,
  text,
  note,
}: Readonly<{ icon: ReactNode; text: string; note: string }>) {
  return (
    <li className="bg-surface-sunken flex items-center gap-3 rounded-lg px-3 py-2.5 text-sm">
      <span
        aria-hidden
        className="text-primary-ink flex size-8 flex-none items-center justify-center rounded-md bg-[color-mix(in_srgb,var(--community-accent)_18%,transparent)]"
      >
        {icon}
      </span>
      <span className="flex min-w-0 flex-col">
        <span className="text-foreground truncate font-medium">{text}</span>
        <span className="text-muted-foreground text-xs">{note}</span>
      </span>
    </li>
  );
}

/** The latest held session, artifact shared and topic proposed, each left out when there is none. */
export function LatelyInside({
  item,
  now,
}: Readonly<{ item: LobbyItem; now: Date }>) {
  const { latestSession, lately } = item;
  const lines: ReactNode[] = [];
  if (latestSession !== null) {
    lines.push(
      <LatelyLine
        key="session"
        icon={<Mic size={16} />}
        text={sessionName(latestSession)}
        note={`Held ${sessionWhen(latestSession, now)}`}
      />,
    );
  }
  if (lately.artifactTitle !== null) {
    lines.push(
      <LatelyLine
        key="artifact"
        icon={<FileText size={16} />}
        text={lately.artifactTitle}
        note="Latest shared"
      />,
    );
  }
  if (lately.topicTitle !== null) {
    lines.push(
      <LatelyLine
        key="topic"
        icon={<MessageSquare size={16} />}
        text={lately.topicTitle}
        note="Topic proposed"
      />,
    );
  }
  if (lines.length === 0) return null;
  return (
    <section className="flex flex-col gap-3">
      <h3 className={LABEL}>Lately inside</h3>
      <ul className="flex flex-col gap-2">{lines}</ul>
    </section>
  );
}

export function WhoIsHere({
  item,
  faces,
}: Readonly<{ item: LobbyItem; faces: LobbyFaces }>) {
  const shown = faces(item, CROWD);
  const more = item.memberCount - shown.length;
  return (
    <section className="flex flex-col gap-3">
      <h3 className={LABEL}>Who is here</h3>
      <div className="flex items-center gap-3">
        <MemberFacepile members={shown} max={CROWD} size="size-9 text-xs" />
        {more > 0 && (
          <span className="text-muted-foreground text-sm font-semibold tabular-nums">
            +{more}
          </span>
        )}
      </div>
    </section>
  );
}

export function NextSession({
  item,
  now,
}: Readonly<{ item: LobbyItem; now: Date }>) {
  const session = item.nextSession;
  return (
    <section className="flex flex-col gap-3">
      <h3 className={LABEL}>Next session</h3>
      <div className="border-border flex flex-col gap-1 rounded-xl border bg-linear-to-br from-[color-mix(in_srgb,var(--community-accent)_14%,transparent)] to-transparent p-4">
        {session === null ? (
          <span className="font-display text-muted-foreground text-lg font-bold">
            {NOTHING_SCHEDULED}
          </span>
        ) : (
          <>
            <span className="font-display text-foreground text-lg font-bold">
              {sessionName(session)}
            </span>
            <span className="text-muted-foreground flex items-center gap-2 text-xs">
              {item.nextSessionThisWeek ? (
                <LiveDot />
              ) : (
                <CalendarDays size={14} aria-hidden />
              )}
              {sessionWhen(session, now)}
            </span>
          </>
        )}
      </div>
    </section>
  );
}
