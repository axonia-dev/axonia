import { EmptyState } from "@/components/patterns/EmptyState";
import { Button } from "@/components/ui/Button";
import type { Practitioner } from "@/features/expertise/domain/expertise";
import { PractitionerCard } from "@/features/expertise/ui/PractitionerCard";

interface Props {
  practitioners: Practitioner[];
  isLoading: boolean;
  isError: boolean;
  /** The result filter: on, only people with activity on the term are listed. */
  demonstratedOnly: boolean;
  onShowAnyone: () => void;
}

function NobodyFound({
  demonstratedOnly,
  onShowAnyone,
}: Readonly<Pick<Props, "demonstratedOnly" | "onShowAnyone">>) {
  if (demonstratedOnly) {
    return (
      <EmptyState
        title="Nobody has done this here"
        description="The filter lists only members with activity on this term in this community."
        action={
          <Button variant="secondary" onPress={onShowAnyone}>
            Show anyone
          </Button>
        }
      />
    );
  }
  return <EmptyState title="Nobody matches" description="Try another term." />;
}

export function PractitionerList({
  practitioners,
  isLoading,
  isError,
  demonstratedOnly,
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
    return (
      <NobodyFound
        demonstratedOnly={demonstratedOnly}
        onShowAnyone={onShowAnyone}
      />
    );
  }
  return (
    <ul className="flex flex-col gap-3">
      {practitioners.map((practitioner) => (
        <li key={practitioner.userId}>
          <PractitionerCard practitioner={practitioner} />
        </li>
      ))}
    </ul>
  );
}
