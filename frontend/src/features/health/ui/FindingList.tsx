import type {
  Finding,
  FindingKind,
  HealthSnapshot,
} from "@/features/health/domain/health";
import { findingText } from "@/features/health/domain/health";
import { cn } from "@/lib/cn";

/** Status colour, never the community's: a finding's mark judges, and the data beside it does not. */
const KIND_MARK: Record<FindingKind, { glyph: string; className: string }> = {
  RISK: { glyph: "!", className: "bg-warning/20 text-warning-ink" },
  GAP: { glyph: "·", className: "bg-surface-sunken text-muted-foreground" },
  STRENGTH: { glyph: "✓", className: "bg-success/20 text-success-ink" },
};

function FindingRow({
  finding,
  snapshot,
}: Readonly<{ finding: Finding; snapshot: HealthSnapshot }>) {
  const mark = KIND_MARK[finding.kind];
  return (
    <li className="border-border flex items-start gap-2.5 border-t py-2.5">
      <span
        aria-hidden="true"
        className={cn(
          "mt-px grid size-[18px] shrink-0 place-items-center rounded-full text-[11px] font-bold",
          mark.className,
        )}
      >
        {mark.glyph}
      </span>
      <p className="text-foreground text-[13px] leading-[1.45]">
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
    <ul className="mt-3 flex flex-col">
      {findings.map((finding) => (
        <FindingRow key={finding.code} finding={finding} snapshot={snapshot} />
      ))}
    </ul>
  );
}
