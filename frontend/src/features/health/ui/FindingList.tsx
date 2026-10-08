import { AlertTriangle, CircleCheck, Info } from "lucide-react";
import type { ComponentType } from "react";

import type {
  Finding,
  FindingKind,
  HealthSnapshot,
} from "@/features/health/domain/health";
import { findingText } from "@/features/health/domain/health";

const KIND_ICON: Record<FindingKind, ComponentType<{ className?: string }>> = {
  RISK: AlertTriangle,
  GAP: Info,
  STRENGTH: CircleCheck,
};

const KIND_TONE: Record<FindingKind, string> = {
  RISK: "text-warning",
  GAP: "text-primary",
  STRENGTH: "text-success",
};

function FindingRow({
  finding,
  snapshot,
}: Readonly<{ finding: Finding; snapshot: HealthSnapshot }>) {
  const Icon = KIND_ICON[finding.kind];
  return (
    <li className="flex items-start gap-3">
      <Icon className={`mt-0.5 size-4 shrink-0 ${KIND_TONE[finding.kind]}`} />
      <p className="text-foreground text-sm">
        {findingText(finding.code, snapshot)}
      </p>
    </li>
  );
}

export function FindingList({
  findings,
  snapshot,
}: Readonly<{ findings: Finding[]; snapshot: HealthSnapshot }>) {
  if (findings.length === 0) {
    return null;
  }
  return (
    <ul className="mt-4 flex flex-col gap-2.5">
      {findings.map((finding) => (
        <FindingRow key={finding.code} finding={finding} snapshot={snapshot} />
      ))}
    </ul>
  );
}
