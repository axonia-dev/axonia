import { useState } from "react";

import { Button } from "@/components/ui/Button";
import type { Proposal } from "@/features/mentoring/domain/mentoring";
import type { ProposalAnswer } from "@/features/mentoring/domain/mentoring-gateway";
import { HAND } from "@/features/mentoring/ui/handwriting";
import { personName } from "@/features/mentoring/ui/PartnerFace";
import {
  ASKED_ONCE_ACCEPTED,
  SHOWN_ONCE_ACCEPTED,
} from "@/features/mentoring/ui/proposal-wording";
import { cn } from "@/lib/cn";

interface Props {
  proposal: Proposal;
  isPending: boolean;
  /** `onRefused` runs when the answer fails, so the seal is whole again. */
  onAnswer: (answer: ProposalAnswer, onRefused?: () => void) => void;
}

const HALF =
  "absolute inset-0 grid place-items-center rounded-full bg-[radial-gradient(circle_at_35%_30%,color-mix(in_srgb,var(--mentee)_55%,white),color-mix(in_srgb,var(--mentee)_78%,black)_72%)] shadow-[inset_0_-3px_6px_color-mix(in_srgb,var(--mentee)_40%,black)]";

function initials(name: string): string {
  const words = name.trim().split(/\s+/);
  const last = words.length > 1 ? (words.at(-1)?.charAt(0) ?? "") : "";
  return `${words[0]?.charAt(0) ?? ""}${last}`.toUpperCase();
}

function Flap() {
  return (
    <svg
      aria-hidden
      viewBox="0 0 100 64"
      preserveAspectRatio="none"
      className="absolute inset-x-0 top-0 h-16 w-full"
    >
      <path
        d="M 0 0 L 50 62 L 100 0 Z"
        fill="color-mix(in srgb, var(--paper-edge) 45%, var(--paper))"
        stroke="var(--paper-edge)"
        strokeWidth={1.5}
        vectorEffect="non-scaling-stroke"
      />
    </svg>
  );
}

function Seal({ name, split }: Readonly<{ name: string; split: boolean }>) {
  const letters = initials(name);
  return (
    <span
      aria-hidden
      data-split={split || undefined}
      className={cn(
        "font-display absolute top-[38px] left-1/2 size-11 -translate-x-1/2 rounded-full text-[13px] font-bold text-[color-mix(in_srgb,var(--mentee)_22%,black)] shadow-md transition-shadow duration-300",
        split && "motion-safe:shadow-none",
      )}
    >
      <span
        className={cn(
          HALF,
          "[--split-turn:-28deg] [--split-x:-16px] [clip-path:inset(0_50%_0_0)]",
          split && "motion-safe:animate-[seal-split_0.4s_ease-in_both]",
        )}
      >
        {letters}
      </span>
      <span
        className={cn(
          HALF,
          "[--split-turn:24deg] [--split-x:14px] [clip-path:inset(0_0_0_50%)]",
          split && "motion-safe:animate-[seal-split_0.4s_ease-in_both]",
        )}
      >
        {letters}
      </span>
    </span>
  );
}

function leadLine(proposal: Proposal): string {
  const name = personName(proposal.partnerName, proposal.partnerId);
  const asks =
    proposal.proposedBy === "MENTEE"
      ? `${name} asks for a mentor`
      : `${name} offers to mentor`;
  return `${asks} · ${proposal.communityName}`;
}

/** A proposal received and still open, sealed like a letter; accepting breaks the seal. */
export function ProposalLetter({
  proposal,
  isPending,
  onAnswer,
}: Readonly<Props>) {
  const [splitting, setSplitting] = useState(false);
  const accept =
    proposal.proposedBy === "MENTEE" ? "Accept as mentor" : "Accept as mentee";
  return (
    <li className="border-paper-edge bg-paper text-ink-sepia relative overflow-hidden rounded-[14px] border px-4 pt-[94px] pb-5 shadow-sm sm:px-8">
      <Flap />
      <Seal
        name={personName(proposal.partnerName, proposal.partnerId)}
        split={splitting}
      />
      <div className="flex flex-col items-center gap-2.5 text-center">
        <p className="text-ink-sepia/80 text-[11px] font-semibold tracking-[0.08em] wrap-anywhere uppercase">
          {leadLine(proposal)}
        </p>
        {proposal.message.trim() !== "" && (
          <p
            className={cn(
              "max-w-prose text-xl leading-7 wrap-anywhere whitespace-pre-line",
              HAND,
            )}
          >
            “{proposal.message}”
          </p>
        )}
        <ul
          aria-label="Topics"
          className="flex flex-wrap justify-center gap-1.5"
        >
          {proposal.topics.map((topic) => (
            <li
              key={topic}
              className="border-paper-edge rounded-full border bg-[color-mix(in_srgb,var(--paper-edge)_35%,var(--paper))] px-2.5 py-0.5 text-[11px] font-medium wrap-anywhere"
            >
              {topic}
            </li>
          ))}
        </ul>
        <div className="mt-1.5 flex flex-wrap justify-center gap-2">
          <Button
            variant="ghost"
            size="sm"
            isDisabled={isPending}
            onPress={() => onAnswer("decline")}
          >
            Decline
          </Button>
          <Button
            size="sm"
            isDisabled={isPending}
            onPress={() => {
              setSplitting(true);
              onAnswer("accept", () => setSplitting(false));
            }}
          >
            {accept}
          </Button>
        </div>
        <p className="text-ink-sepia text-xs">
          {proposal.pairsShown ? SHOWN_ONCE_ACCEPTED : ASKED_ONCE_ACCEPTED}
        </p>
      </div>
    </li>
  );
}
