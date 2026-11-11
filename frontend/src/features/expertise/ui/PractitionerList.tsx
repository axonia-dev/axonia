import { EmptyState } from "@/components/patterns/EmptyState";
import { Button } from "@/components/ui/Button";
import type { Practitioner } from "@/features/expertise/domain/expertise";
import { PractitionerCard } from "@/features/expertise/ui/PractitionerCard";
import type { Who } from "@/features/expertise/ui/who-filter";

interface Props {
  practitioners: Practitioner[];
  isLoading: boolean;
  isError: boolean;
  /** The term searched for, which every open person's card invites questions on. */
  term: string;
  /** The narrowing the people were listed under, which an empty list blames. */
  who: Who;
  onShowAnyone: () => void;
}

function NobodyFound({
  term,
  who,
  onShowAnyone,
}: Readonly<Pick<Props, "term" | "who" | "onShowAnyone">>) {
  if (who === "anyone") {
    return (
      <EmptyState title="Nobody matches" description="Try another term." />
    );
  }
  const showAnyone = (
    <Button variant="secondary" onPress={onShowAnyone}>
      Show anyone
    </Button>
  );
  if (who === "open") {
    return (
      <EmptyState
        title={`Nobody open to questions on ${term}`}
        action={showAnyone}
      />
    );
  }
  return (
    <EmptyState
      title="Nobody has done this here"
      description="The filter lists only members with activity on this term in this community."
      action={showAnyone}
    />
  );
}

export function PractitionerList({
  practitioners,
  isLoading,
  isError,
  term,
  who,
  onShowAnyone,
}: Readonly<Props>) {
  if (isLoading) {
    return <p className="text-muted-foreground text-sm">Loading…</p>;
  }
  if (isError) {
    return (
      <p role="alert" className="text-danger text-sm">
        Could not load the people to ask.
      </p>
    );
  }
  if (practitioners.length === 0) {
    return <NobodyFound term={term} who={who} onShowAnyone={onShowAnyone} />;
  }
  return (
    <ul className="flex flex-col gap-3">
      {practitioners.map((practitioner) => (
        <li key={practitioner.userId}>
          <PractitionerCard practitioner={practitioner} term={term} />
        </li>
      ))}
    </ul>
  );
}
