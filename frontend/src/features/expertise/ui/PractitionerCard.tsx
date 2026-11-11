import { ChevronDown, ChevronUp } from "lucide-react";
import { useState } from "react";

import { MemberAvatar } from "@/components/patterns/MemberAvatar";
import type { Practitioner } from "@/features/expertise/domain/expertise";
import { AskAboutPill } from "@/features/expertise/ui/AskAboutPill";
import { EvidenceDetails } from "@/features/expertise/ui/EvidenceDetails";
import {
  countSummary,
  declarationLine,
} from "@/features/expertise/ui/practitioner-lines";
import { TierBadge } from "@/features/expertise/ui/TierBadge";
import { userHue } from "@/lib/user-hue";

interface Props {
  practitioner: Practitioner;
  /** The area the card answers for, which an open person invites questions on. */
  term: string;
}

function CardLines({ practitioner, term }: Readonly<Props>) {
  const summary = countSummary(practitioner.evidence);
  const declared = declarationLine(practitioner);
  const hasEvidence = practitioner.tier !== "OPEN_TO_QUESTIONS";
  return (
    <>
      {practitioner.orgUnit !== null && (
        <p className="text-muted-foreground text-sm">{practitioner.orgUnit}</p>
      )}
      {summary !== "" && (
        <p className="text-muted-foreground mt-1 text-xs">{summary}</p>
      )}
      {declared !== null && (
        <p className="text-muted-foreground mt-1 text-xs">{declared}</p>
      )}
      {practitioner.openToQuestions && (
        <div className="mt-2">
          <AskAboutPill
            term={term}
            since={hasEvidence ? practitioner.declaredAt : null}
          />
        </div>
      )}
    </>
  );
}

export function PractitionerCard({ practitioner, term }: Readonly<Props>) {
  const [expanded, setExpanded] = useState(false);
  const name = practitioner.displayName ?? practitioner.userId;
  const hue = userHue(practitioner.userId);
  const hasEvidence = practitioner.tier !== "OPEN_TO_QUESTIONS";

  return (
    <article className="border-border bg-surface rounded-md border p-4">
      <div className="flex items-start gap-3">
        <MemberAvatar
          name={name}
          hue={hue}
          subject={practitioner.userId}
          src={practitioner.avatarUrl}
          shape="circle"
          className="size-10 text-sm"
          open={practitioner.openToQuestions}
        />

        <div className="min-w-0 flex-1">
          <div className="flex flex-wrap items-center gap-2">
            <span className="text-foreground font-medium">{name}</span>
            <TierBadge tier={practitioner.tier} />
          </div>
          <CardLines practitioner={practitioner} term={term} />
        </div>

        {hasEvidence && (
          <button
            type="button"
            aria-expanded={expanded}
            aria-label={expanded ? "Collapse evidence" : "Expand evidence"}
            onClick={() => setExpanded((prev) => !prev)}
            className="text-muted-foreground hover:text-foreground shrink-0 cursor-pointer transition-colors"
          >
            {expanded ? <ChevronUp size={18} /> : <ChevronDown size={18} />}
          </button>
        )}
      </div>

      {hasEvidence && expanded && (
        <div className="border-border mt-3 border-t pt-3">
          <EvidenceDetails items={practitioner.evidence.recent} />
        </div>
      )}
    </article>
  );
}
