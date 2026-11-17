import { MailOpen, Send } from "lucide-react";

import { Button } from "@/components/ui/Button";
import { StatusChip } from "@/components/ui/StatusChip";
import type {
  Proposal,
  ProposalStatus,
} from "@/features/mentoring/domain/mentoring";
import type { ProposalAnswer } from "@/features/mentoring/domain/mentoring-gateway";
import { lapseLine } from "@/features/mentoring/ui/my-mentoring/meeting-lines";
import { PartnerFace, personName } from "@/features/mentoring/ui/PartnerFace";
import { declaredOn } from "@/lib/declared-on";
import { useNow } from "@/lib/use-wake-at";

interface Props {
  proposal: Proposal;
  /** Sent proposals are taken back; received ones show here only once answered. */
  direction: "received" | "sent";
  today: string;
  isPending: boolean;
  onAnswer: (answer: ProposalAnswer) => void;
  /** Why a reopen was refused, shown under the proposal. */
  refusal?: string | undefined;
}

type Answering = Omit<Props, "refusal">;

const CLOSED_LABEL: Record<Exclude<ProposalStatus, "OPEN">, string> = {
  ACCEPTED: "Accepted",
  DECLINED: "Not this time",
  WITHDRAWN: "Withdrawn",
  LAPSED: "Lapsed",
};

/** A declined proposal can be reopened by the person who declined it, until its window closes. */
function canReopen(proposal: Proposal, received: boolean, now: Date): boolean {
  const until = proposal.reopenableUntil;
  return (
    received &&
    proposal.status === "DECLINED" &&
    until !== null &&
    now.getTime() < Date.parse(until)
  );
}

function Waiting({
  proposal,
  today,
  isPending,
  onAnswer,
}: Readonly<Answering>) {
  return (
    <div className="ml-auto flex items-center gap-3">
      {proposal.lapsesOn !== null && (
        <span className="text-muted-foreground text-xs whitespace-nowrap">
          {lapseLine(proposal.lapsesOn, today)}
        </span>
      )}
      <Button
        variant="secondary"
        size="sm"
        isDisabled={isPending}
        onPress={() => onAnswer("withdraw")}
      >
        Withdraw
      </Button>
    </div>
  );
}

function Closed({
  proposal,
  direction,
  isPending,
  onAnswer,
}: Readonly<Answering>) {
  const now = useNow();
  if (proposal.status === "OPEN") return null;
  return (
    <div className="ml-auto flex items-center gap-2">
      <StatusChip tone="neutral" label={CLOSED_LABEL[proposal.status]} />
      {canReopen(proposal, direction === "received", now) && (
        <Button
          variant="secondary"
          size="sm"
          isDisabled={isPending}
          onPress={() => onAnswer("reopen")}
        >
          Reopen
        </Button>
      )}
    </div>
  );
}

function detail(proposal: Proposal, direction: Props["direction"]): string {
  const verb = direction === "sent" ? "sent" : "received";
  return [
    `${verb} ${declaredOn(proposal.createdAt)}`,
    proposal.communityName,
    ...proposal.topics,
  ].join(" · ");
}

/** A proposal on its way or already answered: one quiet row, with what can still be done to it. */
export function ProposalSlip({
  proposal,
  refusal,
  ...answering
}: Readonly<Props>) {
  const sent = answering.direction === "sent";
  const Icon = sent ? Send : MailOpen;
  return (
    <li className="flex flex-col gap-2">
      <div className="border-border-strong flex flex-wrap items-center gap-x-3 gap-y-2 rounded-xl border border-dashed px-3 py-2.5">
        <Icon aria-hidden className="text-muted-foreground size-4 flex-none" />
        <PartnerFace
          userId={proposal.partnerId}
          name={proposal.partnerName}
          avatarUrl={proposal.partnerAvatarUrl}
          className="size-7 text-[10px]"
        />
        <p className="min-w-0 flex-1 basis-40 text-sm wrap-anywhere">
          <span className="text-foreground font-semibold">
            {personName(proposal.partnerName, proposal.partnerId)}
          </span>{" "}
          <span className="text-muted-foreground text-xs">
            · {detail(proposal, answering.direction)}
          </span>
        </p>
        {proposal.status === "OPEN" && sent ? (
          <Waiting proposal={proposal} {...answering} />
        ) : (
          <Closed proposal={proposal} {...answering} />
        )}
      </div>
      {refusal !== undefined && (
        <p role="alert" className="text-danger px-3 text-sm">
          {refusal}
        </p>
      )}
    </li>
  );
}
