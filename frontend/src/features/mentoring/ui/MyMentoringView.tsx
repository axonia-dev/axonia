import { type ReactNode, useState } from "react";
import { Link, useParams } from "react-router-dom";

import { useIsDelegated } from "@/auth/delegated-session";
import { EmptyState } from "@/components/patterns/EmptyState";
import { useMyMentoring } from "@/features/mentoring/api/use-mentoring";
import type {
  MyMentoring,
  PairingSummary,
  Proposal,
} from "@/features/mentoring/domain/mentoring";
import { OWN_SIGN_IN_REFUSAL } from "@/features/mentoring/mentoring-errors";
import {
  mentoringPagePath,
  useJournalPath,
} from "@/features/mentoring/ui/mentoring-paths";
import {
  byNextMeeting,
  summitsOf,
  togetherOrdinal,
} from "@/features/mentoring/ui/my-mentoring/mentoring-groups";
import { MyProposals } from "@/features/mentoring/ui/my-mentoring/MyProposals";
import { Summits } from "@/features/mentoring/ui/my-mentoring/Summits";
import { TrailStrip } from "@/features/mentoring/ui/my-mentoring/TrailStrip";

function Section({
  title,
  children,
}: Readonly<{ title: string; children: ReactNode }>) {
  return (
    <section className="flex flex-col gap-3">
      <div className="flex items-center gap-3">
        <h2 className="font-display text-foreground text-[15px] font-semibold">
          {title}
        </h2>
        <span aria-hidden className="bg-border h-px flex-1" />
      </div>
      {children}
    </section>
  );
}

interface Arrival {
  arrivedId: number | null;
  onArrived: () => void;
}

function Pairings({
  title,
  pairings,
  mine,
  all,
  arrival,
}: Readonly<{
  title: string;
  pairings: PairingSummary[];
  mine: MyMentoring;
  all: PairingSummary[];
  arrival: Arrival;
}>) {
  const pathOf = useJournalPath();
  if (pairings.length === 0) return null;
  return (
    <Section title={title}>
      <ul className="flex flex-col gap-3">
        {byNextMeeting(pairings).map((pairing) => (
          <TrailStrip
            key={pairing.id}
            pairing={pairing}
            to={pathOf(pairing.id)}
            together={togetherOrdinal(pairing, all)}
            today={mine.today}
            arriving={pairing.id === arrival.arrivedId}
            onArrived={arrival.onArrived}
          />
        ))}
      </ul>
    </Section>
  );
}

/** `everyone` is every pairing in every community, so a pair's earlier walks count wherever they were. */
function Mentoring({
  mine,
  everyone,
}: Readonly<{ mine: MyMentoring; everyone: PairingSummary[] }>) {
  const [arrivedId, setArrivedId] = useState<number | null>(null);
  const arrival = { arrivedId, onArrived: () => setArrivedId(null) };
  const as = (role: PairingSummary["myRole"]) =>
    mine.ongoing.filter((pairing) => pairing.myRole === role);
  return (
    <>
      <MyProposals mine={mine} onArrived={({ id }) => setArrivedId(id)} />
      <Pairings
        title="My mentors"
        pairings={as("MENTEE")}
        mine={mine}
        all={everyone}
        arrival={arrival}
      />
      <Pairings
        title="My mentees"
        pairings={as("MENTOR")}
        mine={mine}
        all={everyone}
        arrival={arrival}
      />
      {mine.ended.length > 0 && (
        <Section title="Summits">
          <Summits summits={summitsOf(mine.ended)} />
        </Section>
      )}
    </>
  );
}

/** An accepted proposal already shows as its pairing; inside a community, only that community's mentoring shows. */
function shown(mine: MyMentoring, slug: string | undefined): MyMentoring {
  const here = <T extends { communitySlug: string }>(items: T[]) =>
    slug === undefined
      ? items
      : items.filter((item) => item.communitySlug === slug);
  const open = (proposals: Proposal[]) =>
    here(proposals).filter((proposal) => proposal.status !== "ACCEPTED");
  return {
    ...mine,
    received: open(mine.received),
    sent: open(mine.sent),
    ongoing: here(mine.ongoing),
    ended: here(mine.ended),
  };
}

function Empty({ slug }: Readonly<{ slug: string | undefined }>) {
  return slug !== undefined ? (
    <EmptyState
      title="No mentoring in this community yet"
      description={
        <>
          Proposals and pairings appear here. Mentors and mentees are listed on
          the{" "}
          <Link
            to={mentoringPagePath(slug)}
            className="text-primary-ink font-medium underline underline-offset-2"
          >
            Mentoring page
          </Link>
          .
        </>
      }
    />
  ) : (
    <EmptyState
      title="No mentoring yet"
      description="Proposals and pairings appear here. Mentors and mentees are listed on a community’s Mentoring page."
    />
  );
}

function isEmpty(mine: MyMentoring): boolean {
  return (
    mine.received.length +
      mine.sent.length +
      mine.ongoing.length +
      mine.ended.length ===
    0
  );
}

/** Proposals and pairings across every community the member mentors or is mentored in, or of the one community it is opened in. */
export function MyMentoringView() {
  const { slug } = useParams();
  const query = useMyMentoring();
  const data = query.data === undefined ? undefined : shown(query.data, slug);
  if (useIsDelegated()) {
    return (
      <section className="flex flex-col gap-6">
        <h1 className="text-foreground font-display text-xl font-semibold">
          My mentoring
        </h1>
        <p className="text-muted-foreground text-sm">{OWN_SIGN_IN_REFUSAL}</p>
      </section>
    );
  }
  return (
    <section className="flex flex-col gap-6">
      <h1 className="text-foreground font-display text-xl font-semibold">
        My mentoring
      </h1>
      {query.isLoading && (
        <p className="text-muted-foreground text-sm">Loading…</p>
      )}
      {!query.isLoading && data === undefined && (
        <p role="alert" className="text-danger text-sm">
          Could not load mentoring.
        </p>
      )}
      {data !== undefined && isEmpty(data) && <Empty slug={slug} />}
      {query.data !== undefined && data !== undefined && (
        <Mentoring
          mine={data}
          everyone={[...query.data.ongoing, ...query.data.ended]}
        />
      )}
    </section>
  );
}
