import { useState } from "react";
import { Link } from "react-router-dom";

import { AreaTag } from "@/components/ui/AreaTag";
import { Button, buttonClass } from "@/components/ui/Button";
import { StatusChip } from "@/components/ui/StatusChip";
import type {
  DirectoryPerson,
  MentoringRole,
} from "@/features/mentoring/domain/mentoring";
import { dashedNoteClass } from "@/features/mentoring/ui/dashed-note";
import { PartnerFace, personName } from "@/features/mentoring/ui/PartnerFace";
import { proposalWording } from "@/features/mentoring/ui/proposal-wording";
import { ProposeDialog } from "@/features/mentoring/ui/ProposeDialog";
import { cn } from "@/lib/cn";
import { dayAndMonth } from "@/lib/declared-on";

type Person = DirectoryPerson;

/** Why a list's cards stay hidden from a viewer not on its other side, and how many it holds. */
interface ClosedList {
  reason: string;
  count: string;
}

interface Props {
  id?: string;
  slug: string;
  title: string;
  empty: string;
  people: Person[];
  /** The viewer's own areas that match this list's: marked on each card and counted. */
  sharedAreas: string[];
  /** Set when the viewer is not on the side that sees this list. */
  closed: ClosedList | null;
  /** The role of the person proposing to anyone in this list. */
  proposerRole: MentoringRole;
  /** Whose page this is, who is never offered a proposal to themselves. */
  selfId: string | undefined;
  archived: boolean;
}

const STATE_LABEL = {
  PROPOSAL_SENT: "Proposal sent",
  PAIRED: "Paired",
} as const;

function Areas({
  areas,
  shared,
}: Readonly<{ areas: string[]; shared: ReadonlySet<string> }>) {
  if (areas.length === 0) return null;
  return (
    <ul className="flex flex-wrap gap-1.5">
      {areas.map((area) => (
        <li key={area} className="max-w-full">
          <AreaTag area={area} shared={shared.has(area)} />
        </li>
      ))}
    </ul>
  );
}

function InCommon({ count }: Readonly<{ count: number }>) {
  if (count === 0) return null;
  return (
    <p className="text-muted-foreground text-xs">
      <span className="text-foreground font-semibold">
        {count === 1 ? "1 area" : `${count} areas`}
      </span>{" "}
      in common
    </p>
  );
}

function declinedRecently(person: Person): boolean {
  return person.proposalState === "DECLINED_RECENTLY";
}

interface ActionProps {
  slug: string;
  person: Person;
  proposerRole: MentoringRole;
  canPropose: boolean;
  onPropose: () => void;
}

function Action({
  slug,
  person,
  proposerRole,
  canPropose,
  onPropose,
}: Readonly<ActionProps>) {
  if (person.proposalState === "PROPOSAL_RECEIVED") {
    return (
      <Link
        to={`/communities/${slug}/me/mentoring`}
        className={buttonClass("primary", "sm")}
      >
        Answer proposal
      </Link>
    );
  }
  if (person.proposalState === "DECLINED_RECENTLY") return null;
  if (person.proposalState !== null) {
    return (
      <StatusChip
        tone={person.proposalState === "PROPOSAL_SENT" ? "primary" : "neutral"}
        label={STATE_LABEL[person.proposalState]}
      />
    );
  }
  if (!canPropose || !person.proposable) return null;
  const { label, named } = proposalWording(proposerRole);
  return (
    <Button
      variant="secondary"
      size="sm"
      aria-label={named(personName(person.displayName))}
      onPress={onPropose}
    >
      {label}
    </Button>
  );
}

function PersonCard({
  slug,
  person,
  shared,
  proposerRole,
  canPropose,
}: Readonly<{
  slug: string;
  person: Person;
  shared: ReadonlySet<string>;
  proposerRole: MentoringRole;
  canPropose: boolean;
}>) {
  const [open, setOpen] = useState(false);
  const quiet = declinedRecently(person);
  const inCommon = person.areas.filter((area) => shared.has(area)).length;
  return (
    <li
      className={cn(
        "border-border bg-surface flex flex-col gap-3 rounded-2xl border p-4 motion-safe:transition-transform motion-safe:hover:-translate-y-0.5",
        quiet && "opacity-60",
      )}
    >
      <div className="flex flex-wrap items-center gap-3">
        <PartnerFace
          userId={person.userId}
          name={person.displayName}
          avatarUrl={person.avatarUrl}
        />
        <div className="flex min-w-32 flex-1 flex-col">
          <p className="text-foreground text-sm font-semibold wrap-anywhere">
            {personName(person.displayName)}
          </p>
          {quiet && person.proposalsOpenOn !== null && (
            <p className="text-muted-foreground text-xs">
              Proposals open again {dayAndMonth(person.proposalsOpenOn)}
            </p>
          )}
        </div>
        <div className="ml-auto">
          <Action
            slug={slug}
            person={person}
            proposerRole={proposerRole}
            canPropose={canPropose}
            onPropose={() => setOpen(true)}
          />
        </div>
      </div>
      <Areas areas={person.areas} shared={shared} />
      <InCommon count={inCommon} />
      {open && (
        <ProposeDialog
          slug={slug}
          person={person}
          asRole={proposerRole}
          isOpen
          onOpenChange={setOpen}
        />
      )}
    </li>
  );
}

function Closed({ closed }: Readonly<{ closed: ClosedList }>) {
  return (
    <div className={dashedNoteClass}>
      <p>{closed.reason}</p>
      <p className="text-xs">{closed.count}</p>
    </div>
  );
}

/** People in the order the server gives (by name), with a recent decline quietly last. */
function inListOrder(people: Person[]): Person[] {
  return [
    ...people.filter((person) => !declinedRecently(person)),
    ...people.filter(declinedRecently),
  ];
}

export function DirectoryList({
  id,
  slug,
  title,
  empty,
  people,
  sharedAreas,
  closed,
  proposerRole,
  selfId,
  archived,
}: Readonly<Props>) {
  const canPropose = !archived;
  const shared = new Set(sharedAreas);
  return (
    <section id={id} className="flex min-w-0 flex-col gap-3">
      <h2 className="font-display text-foreground text-lg font-semibold">
        {title}
      </h2>
      {closed !== null && <Closed closed={closed} />}
      {closed === null && people.length === 0 && (
        <p className="text-muted-foreground text-sm">{empty}</p>
      )}
      {closed === null && people.length > 0 && (
        <ul className="flex flex-col gap-2.5">
          {inListOrder(people).map((person) => (
            <PersonCard
              key={person.userId}
              slug={slug}
              person={person}
              shared={shared}
              proposerRole={proposerRole}
              canPropose={canPropose && person.userId !== selfId}
            />
          ))}
        </ul>
      )}
    </section>
  );
}
