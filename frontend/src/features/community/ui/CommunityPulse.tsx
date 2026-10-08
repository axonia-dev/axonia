import type { IconName } from "lucide-react/dynamic";
import { Link } from "react-router-dom";

import { AccentIcon } from "@/components/ui/AccentIcon";
import { AnimatedCount } from "@/components/ui/AnimatedCount";
import { SectionCard } from "@/components/ui/SectionCard";
import { Skeleton } from "@/components/ui/Skeleton";
import { useMembers } from "@/features/community/api/use-membership";
import { joinedSince } from "@/features/community/domain/membership";
import {
  useLastHeldSession,
  useSessionsHeldSince,
} from "@/features/events/api/use-sessions";
import { useTopics } from "@/features/events/api/use-topics";
import {
  distinctPresenters,
  type Session,
  slotCount,
} from "@/features/events/domain/session";
import { useArtifacts } from "@/features/knowledge/api/use-artifacts";
import { relativeTime, startOfMonthIso, startOfQuarterIso } from "@/lib/period";
import { plural, pluralWord } from "@/lib/plural";
import { useNow } from "@/lib/use-wake-at";

/** null renders a loading skeleton; "-" is the degraded/error value; a number/string is the live value. */
type VitalValue = number | string | null;

const NO_VALUE = "-";

/** The noun a figure reads with: singular beside a 1, plural beside anything else or no number. */
function nounFor(value: VitalValue | undefined, noun: string): string {
  return pluralWord(typeof value === "number" ? value : 0, noun);
}

interface VitalProps {
  label: string;
  /** Undefined draws no figure at all. */
  value?: VitalValue | undefined;
  detail?: string | undefined;
  /** A positive change rendered green before the detail, as "+5". */
  delta?: number | undefined;
  icon: IconName;
  accent: string;
  to: string;
}

function VitalCount({ value }: Readonly<{ value: VitalValue }>) {
  if (value === null) {
    return <Skeleton className="h-6 w-10" />;
  }
  if (typeof value === "number") {
    return (
      <AnimatedCount
        value={value}
        className="font-display text-foreground text-xl leading-none font-bold"
      />
    );
  }
  return (
    <span className="font-display text-foreground text-xl leading-none font-bold">
      {value}
    </span>
  );
}

function Vital({
  label,
  value,
  detail,
  delta,
  icon,
  accent,
  to,
}: Readonly<VitalProps>) {
  return (
    <Link
      to={to}
      className="hover:bg-surface-sunken flex items-center gap-3 rounded-md px-2 py-2 transition-colors"
    >
      <AccentIcon name={icon} accent={accent} size={19} className="size-10" />
      <div className="min-w-0">
        <div className="flex items-baseline gap-2">
          {value !== undefined && <VitalCount value={value} />}
          <span className="text-foreground text-sm font-semibold">{label}</span>
        </div>
        {detail && (
          <p className="text-muted-foreground mt-0.5 text-xs">
            {delta !== undefined && (
              <span className="text-success font-semibold">+{delta} </span>
            )}
            {detail}
          </p>
        )}
      </div>
    </Link>
  );
}

interface CountQuery {
  isPending: boolean;
  isError: boolean;
  data: { totalElements: number } | undefined;
}

function countOf(query: CountQuery): VitalValue {
  if (query.isPending) {
    return null;
  }
  return query.isError ? NO_VALUE : (query.data?.totalElements ?? NO_VALUE);
}

const UNAVAILABLE = "Unavailable right now";

interface Annotation {
  delta?: number;
  detail?: string;
}

/** A month with nothing in it says so in words: a green "+0" reads as growth. */
function thisMonth(count: number, verb: string): Annotation {
  return count === 0
    ? { detail: `None ${verb} this month` }
    : { delta: count, detail: `${verb} this month` };
}

function libraryVitalAnnotation(
  library: CountQuery,
  libraryThisMonth: CountQuery,
): Annotation {
  if (library.isError) {
    return { detail: UNAVAILABLE };
  }
  if (libraryThisMonth.data) {
    return thisMonth(libraryThisMonth.data.totalElements, "added");
  }
  return {};
}

/**
 * Signs-of-life vitals about the community as a whole, never individuals
 * ranked against each other. Each vital owns its loading/error state so one
 * slow context degrades to a dash instead of blanking the rail.
 */
export function CommunityPulse({
  slug,
  memberCount,
}: Readonly<{ slug: string; memberCount: number }>) {
  const now = useNow();
  const monthStart = startOfMonthIso(now);
  const quarterStart = startOfQuarterIso(now);
  const base = `/communities/${slug}`;

  const backlog = useTopics(slug, { status: "PROPOSED", page: 0, size: 1 });
  const library = useArtifacts(slug, { page: 0, size: 1 });
  const libraryThisMonth = useArtifacts(slug, {
    page: 0,
    size: 1,
    from: monthStart,
  });
  const members = useMembers(slug, { page: 0, size: 100 });
  const lastSession = useLastHeldSession(slug);
  const heldThisQuarter = useSessionsHeldSince(slug, quarterStart);

  const joined = members.data
    ? thisMonth(joinedSince(members.data.content, monthStart), "joined")
    : {};

  const { delta: libraryDelta, detail: libraryDetail } = libraryVitalAnnotation(
    library,
    libraryThisMonth,
  );

  const last = lastSessionVital(lastSession, now);
  const backlogCount = countOf(backlog);

  let quarterValue: VitalValue = null;
  if (!heldThisQuarter.isPending) {
    quarterValue = heldThisQuarter.isError
      ? NO_VALUE
      : (heldThisQuarter.data ?? NO_VALUE);
  }

  return (
    <SectionCard
      title="Community pulse"
      icon="activity"
      accent="var(--success)"
    >
      <div className="flex flex-col">
        <Vital
          icon="layers"
          accent="var(--warning)"
          to={`${base}/backlog`}
          label="in the backlog"
          value={backlogCount}
          detail={
            backlog.isError
              ? UNAVAILABLE
              : `${nounFor(backlogCount, "topic")} waiting to be scheduled`
          }
        />
        <Vital
          icon="library"
          accent="var(--secondary)"
          to={`${base}/library`}
          label="in the Library"
          value={countOf(library)}
          delta={libraryDelta}
          detail={libraryDetail}
        />
        <Vital
          icon="users"
          accent="var(--primary)"
          to={`${base}/settings/members`}
          label={nounFor(memberCount, "member")}
          value={memberCount}
          delta={joined.delta}
          detail={joined.detail}
        />
        <Vital
          icon="mic"
          accent="var(--secondary)"
          to={`${base}/schedule`}
          label={
            last.label ?? `${nounFor(last.value, "presenter")} last session`
          }
          value={last.value}
          detail={last.detail}
        />
        <Vital
          icon="calendar-check"
          accent="var(--success)"
          to={`${base}/schedule`}
          label={`${nounFor(quarterValue, "session")} this quarter`}
          value={quarterValue}
          detail={
            heldThisQuarter.isError
              ? UNAVAILABLE
              : "held since the quarter began"
          }
        />
      </div>
    </SectionCard>
  );
}

interface SessionQuery {
  isPending: boolean;
  isError: boolean;
  data: Session | null | undefined;
}

/** Derives the "presenters last session" vital value + detail from the last-session read. */
function lastSessionVital(
  query: SessionQuery,
  now: Date,
): { value?: VitalValue; label?: string; detail?: string } {
  if (query.isPending) {
    return { value: null };
  }
  if (query.isError) {
    return { value: NO_VALUE, detail: UNAVAILABLE };
  }
  if (!query.data) {
    return { label: "No sessions held yet" };
  }
  const session = query.data;
  return {
    value: distinctPresenters(session).length,
    detail: `${plural(slotCount(session), "topic")} · ${relativeTime(session.scheduledAt, now)}`,
  };
}
