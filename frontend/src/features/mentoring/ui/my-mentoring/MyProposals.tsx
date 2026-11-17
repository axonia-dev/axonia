import { useState } from "react";

import { notify } from "@/components/ui/toast";
import { useAnswerProposal } from "@/features/mentoring/api/use-mentoring";
import type {
  MyMentoring,
  PairingSummary,
  Proposal,
} from "@/features/mentoring/domain/mentoring";
import type { ProposalAnswer } from "@/features/mentoring/domain/mentoring-gateway";
import { mentoringErrorMessage } from "@/features/mentoring/mentoring-errors";
import { ProposalLetter } from "@/features/mentoring/ui/my-mentoring/ProposalLetter";
import { ProposalSlip } from "@/features/mentoring/ui/my-mentoring/ProposalSlip";
import { onFailure } from "@/features/mentoring/ui/notify-failure";

interface Refusal {
  proposalId: number;
  line: string;
}

/** Letters to answer, then every other proposal as a slip; accepting hands the new pairing up before it first shows. */
export function MyProposals({
  mine,
  onArrived,
}: Readonly<{
  mine: MyMentoring;
  onArrived: (pairing: PairingSummary) => void;
}>) {
  const answer = useAnswerProposal(onArrived);
  const [refusal, setRefusal] = useState<Refusal | null>(null);

  function send(
    proposal: Proposal,
    choice: ProposalAnswer,
    onRefused?: () => void,
  ) {
    setRefusal(null);
    answer.mutate(
      {
        id: proposal.id,
        communitySlug: proposal.communitySlug,
        answer: choice,
      },
      {
        onSuccess: () => {
          if (choice === "decline") offerUndo(proposal);
        },
        onError: (error) => {
          onRefused?.();
          if (choice !== "reopen") return onFailure(error);
          setRefusal({
            proposalId: proposal.id,
            line: mentoringErrorMessage(error),
          });
        },
      },
    );
  }

  /** A toast's action can be pressed again before it closes; the undo reopens once. */
  function offerUndo(proposal: Proposal) {
    let undone = false;
    notify("Declined", {
      label: "Undo",
      onPress: () => {
        if (undone) return;
        undone = true;
        send(proposal, "reopen");
      },
    });
  }

  function slip(proposal: Proposal, direction: "received" | "sent") {
    return (
      <ProposalSlip
        key={proposal.id}
        proposal={proposal}
        direction={direction}
        today={mine.today}
        isPending={answer.isPending}
        onAnswer={(choice) => send(proposal, choice)}
        refusal={refusal?.proposalId === proposal.id ? refusal.line : undefined}
      />
    );
  }

  const letters = mine.received.filter((p) => p.status === "OPEN");
  const answered = mine.received.filter((p) => p.status !== "OPEN");
  return (
    <>
      {letters.length > 0 && (
        <ul aria-label="Proposals to answer" className="flex flex-col gap-4">
          {letters.map((proposal) => (
            <ProposalLetter
              key={proposal.id}
              proposal={proposal}
              isPending={answer.isPending}
              onAnswer={(choice, onRefused) =>
                send(proposal, choice, onRefused)
              }
            />
          ))}
        </ul>
      )}
      {answered.length + mine.sent.length > 0 && (
        <ul aria-label="Proposals" className="flex flex-col gap-2">
          {answered.map((proposal) => slip(proposal, "received"))}
          {mine.sent.map((proposal) => slip(proposal, "sent"))}
        </ul>
      )}
    </>
  );
}
