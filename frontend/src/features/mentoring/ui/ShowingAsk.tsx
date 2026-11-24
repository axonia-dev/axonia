import { Button } from "@/components/ui/Button";
import { useMe } from "@/features/identity/api/use-me";
import { useShowing } from "@/features/mentoring/api/use-mentoring";
import type { Journal } from "@/features/mentoring/domain/mentoring";
import type { ShowingAnswer } from "@/features/mentoring/domain/mentoring-gateway";
import { Ask } from "@/features/mentoring/ui/Ask";
import { onFailure } from "@/features/mentoring/ui/notify-failure";
import { personName } from "@/features/mentoring/ui/PartnerFace";

/** Who the community would see mentoring whom; without the reader's own name, only that the pairing would show. */
function wouldSee(journal: Journal, selfName: string | undefined): string {
  const { pairing } = journal;
  if (selfName === undefined) {
    return "Members of the community would see this pairing.";
  }
  const partner = personName(pairing.partnerName, pairing.partnerId);
  const [mentor, mentee] =
    pairing.myRole === "MENTOR" ? [selfName, partner] : [partner, selfName];
  return `Members of the community would see that ${mentor} mentors ${mentee}.`;
}

function partnerFirstName(journal: Journal): string {
  const { pairing } = journal;
  return (
    pairing.partnerGivenName ??
    personName(pairing.partnerName, pairing.partnerId)
  );
}

/** The one-time ask of a pairing begun under the old promise of privacy: shown only once both say yes. */
export function ShowingAsk({ journal }: Readonly<{ journal: Journal }>) {
  const { pairing, showing } = journal;
  const answer = useShowing(pairing.id, pairing.communitySlug);
  const selfName = useMe().data?.displayName;
  if (
    !journal.writable ||
    pairing.endedAt !== null ||
    showing.visibility !== "ASKING"
  ) {
    return null;
  }
  const say = (value: ShowingAnswer) =>
    answer.mutate(value, { onError: onFailure });
  if (showing.iSaidYes) {
    const first = partnerFirstName(journal);
    return (
      <Ask
        title={`Waiting for ${first}`}
        lines={[
          `The pairing shows on the Mentoring page once ${first} says yes too.`,
        ]}
      >
        <Button
          variant="outline"
          size="sm"
          isDisabled={answer.isPending}
          onPress={() => say("UNSAID")}
        >
          Change my mind
        </Button>
      </Ask>
    );
  }
  return (
    <Ask
      title="Show this pairing on the Mentoring page?"
      lines={[
        wouldSee(journal, selfName),
        "The journal stays private to the pair.",
      ]}
      note="Keep private cannot be undone."
    >
      <Button
        variant="outline"
        size="sm"
        isDisabled={answer.isPending}
        onPress={() => say("NO")}
      >
        Keep private
      </Button>
      <Button
        size="sm"
        isDisabled={answer.isPending}
        onPress={() => say("YES")}
      >
        Show it
      </Button>
    </Ask>
  );
}
