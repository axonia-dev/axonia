import {
  useMyReactionProposals,
  useReactionEligibility,
  useRemoveReaction,
} from "@/features/recognition/api/use-reactions";
import { recognitionErrorMessage } from "@/features/recognition/recognition-errors";
import {
  headroom,
  OwnProposalRow,
} from "@/features/recognition/ui/reaction-rows";

/**
 * The proposals a member already has waiting, each with the Withdraw that frees a slot.
 *
 * It sits in the propose dialog because that is where the open-proposal cap bites: a member who
 * has filled it is refused at the moment of proposing, and the remedy has to be within reach of
 * that refusal rather than on a screen the cap never mentions. The picker itself deliberately
 * shows approved reactions only - a pending one is not usable, so a row for it there would be an
 * item that does nothing.
 *
 * Nothing is drawn at all when nothing is waiting: absence, not a zero.
 */
export function MyProposals() {
  const proposals = useMyReactionProposals();
  const eligibility = useReactionEligibility();
  const withdraw = useRemoveReaction();

  const waiting = proposals.data ?? [];
  if (waiting.length === 0) {
    return null;
  }

  return (
    <section className="border-border flex flex-col gap-2 border-b pb-4">
      <h3 className="text-sm font-semibold">
        Waiting for approval{" "}
        <span className="text-muted-foreground font-normal">
          {headroom(waiting.length, eligibility.data?.openProposalLimit)} open
        </span>
      </h3>
      {withdraw.isError && (
        <p role="alert" className="text-danger text-sm">
          {recognitionErrorMessage(withdraw.error)}
        </p>
      )}
      <ul className="flex flex-col gap-2">
        {waiting.map((definition) => (
          <OwnProposalRow
            key={definition.id}
            definition={definition}
            isWithdrawing={
              withdraw.isPending && withdraw.variables === definition.id
            }
            onWithdraw={() => withdraw.mutate(definition.id)}
          />
        ))}
      </ul>
    </section>
  );
}
