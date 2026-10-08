import { ChevronDown, ChevronUp } from "lucide-react";
import { useState } from "react";

import { MemberAvatar } from "@/components/patterns/MemberAvatar";
import type {
  Evidence,
  Practitioner,
} from "@/features/expertise/domain/expertise";
import { EvidenceDetails } from "@/features/expertise/ui/EvidenceDetails";
import { TierBadge } from "@/features/expertise/ui/TierBadge";
import { declaredOn } from "@/lib/declared-on";
import { userHue } from "@/lib/user-hue";

interface Props {
  practitioner: Practitioner;
}

function countSummary(evidence: Evidence): string {
  const parts: string[] = [];
  if (evidence.presentedCount > 0) {
    parts.push(
      `hosted ${evidence.presentedCount} ${evidence.presentedCount === 1 ? "session" : "sessions"}`,
    );
  }
  if (evidence.contributedCount > 0) {
    parts.push(
      `${evidence.contributedCount} ${evidence.contributedCount === 1 ? "artifact" : "artifacts"}`,
    );
  }
  if (evidence.offeredCount > 0) {
    parts.push(
      `offered ${evidence.offeredCount} ${evidence.offeredCount === 1 ? "topic" : "topics"}`,
    );
  }
  return parts.join(" · ");
}

function declarationLine(practitioner: Practitioner): string | null {
  if (!practitioner.openToQuestions || practitioner.declaredAt === null) {
    return null;
  }
  const on = declaredOn(practitioner.declaredAt);
  return practitioner.tier === "OPEN_TO_QUESTIONS"
    ? `Declared ${on}, no activity yet`
    : `Open to questions since ${on}`;
}

export function PractitionerCard({ practitioner }: Readonly<Props>) {
  const [expanded, setExpanded] = useState(false);
  const name = practitioner.displayName ?? practitioner.userId;
  const hue = userHue(practitioner.userId);
  const summary = countSummary(practitioner.evidence);
  const declared = declarationLine(practitioner);
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
        />

        <div className="min-w-0 flex-1">
          <div className="flex flex-wrap items-center gap-2">
            <span className="text-foreground font-medium">{name}</span>
            <TierBadge tier={practitioner.tier} />
          </div>
          {practitioner.orgUnit !== null && (
            <p className="text-muted-foreground text-sm">
              {practitioner.orgUnit}
            </p>
          )}
          {summary !== "" && (
            <p className="text-muted-foreground mt-1 text-xs">{summary}</p>
          )}
          {declared !== null && (
            <p className="text-muted-foreground mt-1 text-xs">{declared}</p>
          )}
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
