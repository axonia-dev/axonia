import type { ReactNode } from "react";
import { Link } from "react-router-dom";

import type { FacepileMember } from "@/components/patterns/MemberFacepile";
import { MemberFacepile } from "@/components/patterns/MemberFacepile";
import { Skeleton } from "@/components/ui/Skeleton";
import { useIsOrganizer } from "@/features/community/api/use-organizer";
import {
  isStale,
  type NextUp,
  nextUpOf,
  recapOf,
  type SessionRecap,
} from "@/features/community/domain/session-recap";
import { MarqueeNext } from "@/features/community/ui/MarqueeNext";
import { factsOf, RecapPills } from "@/features/community/ui/MarqueePills";
import { MarqueeTalks } from "@/features/community/ui/MarqueeTalks";
import { useActionItems } from "@/features/events/api/use-action-items";
import {
  useLastHeldSession,
  useUpcomingSessions,
} from "@/features/events/api/use-sessions";
import type { ActionItemListParams } from "@/features/events/domain/events-gateway";
import type { Session } from "@/features/events/domain/session";
import { useMe } from "@/features/identity/api/use-me";
import { useUsers } from "@/features/identity/api/use-users";
import type { User } from "@/features/identity/domain/user";
import { useSessionResources } from "@/features/knowledge/api/use-session-resources";
import { relativeTime } from "@/lib/period";
import { useNow } from "@/lib/use-wake-at";
import { userHue } from "@/lib/user-hue";

const NAMES = new Intl.ListFormat("en", { style: "long", type: "conjunction" });

/** The session's own action points: raised here, still open. The server filters both, so the
 *  pill counts what this session left behind rather than the whole community's backlog. */
function actionPointParams(sessionId: number): ActionItemListParams {
  return { outcome: "OPEN", originSessionId: sessionId, page: 0, size: 50 };
}

function faceOf(userId: string, user: User | undefined): FacepileMember {
  return {
    id: userId,
    name: user?.displayName ?? userId,
    hue: userHue(userId),
    src: user?.avatarUrl ?? null,
  };
}

interface PresentersProps {
  userIds: readonly string[];
  users: ReadonlyMap<string, User>;
  facts: string;
}

/**
 * Who presented, as faces, with the session's own facts beside them.
 *
 * Faces rather than the names written out, as on every other surface that shows a group of people:
 * three names spelled out is the widest line in the marquee and the one carrying the least. The
 * talks below still name the presenter of each, and the label carries every name to a reader who
 * cannot see the faces, since the monogram avatars are drawn aria-hidden.
 */
function MarqueePresenters({
  userIds,
  users,
  facts,
}: Readonly<PresentersProps>) {
  const faces = userIds.map((userId) => faceOf(userId, users.get(userId)));
  return (
    <div className="text-muted-foreground mt-2 flex flex-wrap items-center gap-x-2 gap-y-1 text-sm">
      {faces.length > 0 && (
        <span
          role="img"
          aria-label={`${NAMES.format(faces.map((face) => face.name))} presented`}
        >
          <MemberFacepile members={faces} size="size-6 text-[0.6rem]" />
        </span>
      )}
      <span>{facts}</span>
    </div>
  );
}

/**
 * The loud surface and the quiet one. A session further back than the freshness window drops the
 * gradient: the page's biggest, brightest object announcing a session from months ago tells a
 * visitor the community is alive when it is not.
 */
const LOUD =
  "border-border-strong bg-[radial-gradient(120%_140%_at_88%_-20%,color-mix(in_srgb,var(--secondary)_26%,transparent),transparent_60%),radial-gradient(110%_130%_at_4%_120%,color-mix(in_srgb,var(--community-accent,var(--primary))_22%,transparent),transparent_62%)] bg-surface";
const QUIET = "border-border bg-surface";

interface RecapBodyProps {
  slug: string;
  session: Session;
}

/**
 * Everything the recap needs from the wire, resolved in one place.
 *
 * Extracted rather than inlined because each `??` that defaults an unresolved query counts against
 * the component's own complexity budget, and a body made mostly of those defaults is where the
 * rendering it exists for stops being readable.
 */
function useRecap(slug: string, session: Session): SessionRecap {
  const actionPoints = useActionItems(slug, actionPointParams(session.id));
  const resources = useSessionResources(slug, session.id);
  const me = useMe();
  return recapOf({
    session,
    actionPoints: actionPoints.data?.content ?? [],
    resourceCount: resources.data?.length ?? 0,
    meId: me.data?.id ?? null,
    now: useNow(),
  });
}

/**
 * The recap body, mounted only once there is a held session to describe: its action points and its
 * resources are keyed by that session's id, so reading them anywhere higher would mean a query
 * with nothing to ask about.
 */
function MarqueeRecap({ slug, session }: Readonly<RecapBodyProps>) {
  const recap = useRecap(slug, session);
  const users = useUsers(recap.presenterUserIds);
  const byId = new Map(Object.entries(users.data ?? {}));
  const now = useNow();

  return (
    <div className="px-5 pt-4 pb-5">
      <div className="flex flex-wrap items-baseline justify-between gap-x-4 gap-y-1">
        <p className="text-muted-foreground text-xs font-bold tracking-[0.11em] uppercase">
          Last session · held {relativeTime(recap.heldAt, now)}
        </p>
        {recap.attendeeCount > 0 && (
          <p className="text-muted-foreground text-xs">
            {recap.attendeeCount} in the room
          </p>
        )}
      </div>
      <h2 className="font-display text-foreground mt-2 text-xl leading-tight font-bold sm:text-2xl">
        <Link
          to={`/communities/${slug}/sessions/${recap.sessionId}`}
          className="underline-offset-4 hover:underline"
        >
          {recap.title}
        </Link>
      </h2>
      <MarqueePresenters
        userIds={recap.presenterUserIds}
        users={byId}
        facts={factsOf(recap)}
      />
      {recap.notes !== null && (
        <p className="text-foreground border-primary/40 mt-3 border-l-2 pl-3 text-sm leading-relaxed">
          {recap.notes}
        </p>
      )}
      <MarqueeTalks
        talks={recap.strip}
        moreTalks={recap.moreTalks}
        users={byId}
      />
      <RecapPills recap={recap} />
    </div>
  );
}

/** Nothing held, nothing booked. Only an organizer can act on that, so only an organizer is told. */
function NothingYet({ slug }: Readonly<{ slug: string }>) {
  return (
    <section className="border-border-strong flex flex-col items-center gap-3 rounded-lg border border-dashed p-6 text-center">
      <div>
        <p className="font-display text-foreground text-base font-bold">
          No session yet
        </p>
        <p className="text-muted-foreground mt-1 text-sm">
          The first one is worth scheduling.
        </p>
      </div>
      <Link
        to={`/communities/${slug}/schedule`}
        className="bg-primary text-primary-foreground hover:bg-primary-strong rounded-full px-4 py-1.5 text-sm font-semibold transition-colors"
      >
        Schedule a session
      </Link>
    </section>
  );
}

interface ShellProps {
  stale: boolean;
  children: ReactNode;
}

function Shell({ stale, children }: Readonly<ShellProps>) {
  return (
    <section
      aria-label="Session catch-up"
      className={`overflow-hidden rounded-lg border shadow-sm ${stale ? QUIET : LOUD}`}
    >
      {children}
    </section>
  );
}

interface BodyProps {
  slug: string;
  session: Session | null;
  next: NextUp | null;
}

/**
 * The shell and its two halves. Split out so the component above is left with the three questions
 * it actually answers: is the read done, is there anything to say, and who is asking.
 */
function MarqueeBody({ slug, session, next }: Readonly<BodyProps>) {
  const now = useNow();
  if (session === null) {
    return (
      <Shell stale={false}>
        {next !== null && <MarqueeNext slug={slug} next={next} />}
      </Shell>
    );
  }
  return (
    <Shell stale={isStale(session.scheduledAt, now)}>
      <MarqueeRecap slug={slug} session={session} />
      {next !== null && <MarqueeNext slug={slug} next={next} />}
    </Shell>
  );
}

/** A failed read yields nothing to say, which the marquee renders as nothing rather than as holes. */
function answered<T>(query: {
  isError: boolean;
  data: T | undefined;
}): T | null {
  return query.isError ? null : (query.data ?? null);
}

/**
 * The community's temporal spine: what the last held session was, and what follows it.
 *
 * The two halves are independent. A community that has held nothing but has something booked still
 * gets the foot, which is the only place the overview states the next session now that the hero's
 * chip and the rail's own card are gone. A failed read renders nothing at all rather than a hero
 * with holes in it.
 */
export function SessionMarquee({ slug }: Readonly<{ slug: string }>) {
  const lastHeld = useLastHeldSession(slug);
  const upcoming = useUpcomingSessions(slug, { page: 0, size: 1 });
  const isOrganizer = useIsOrganizer(slug);

  if (lastHeld.isPending || upcoming.isPending) {
    return <Skeleton className="h-52 rounded-lg" />;
  }

  const session = answered(lastHeld);
  const next: NextUp | null = nextUpOf(answered(upcoming)?.content[0] ?? null);

  if (session === null && next === null) {
    return isOrganizer ? <NothingYet slug={slug} /> : null;
  }

  return <MarqueeBody slug={slug} session={session} next={next} />;
}
