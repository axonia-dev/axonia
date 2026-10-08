import { Button } from "@/components/ui/Button";
import type { ContributionKind } from "@/features/recognition/domain/contribution-filter";
import { cn } from "@/lib/cn";

const KINDS: ReadonlyArray<{ id: ContributionKind; label: string }> = [
  { id: "ALL", label: "All" },
  { id: "SLOT_PRESENTED", label: "Talks" },
  { id: "ARTIFACT_CAPTURED", label: "Artifacts" },
];

const chipBase =
  "inline-flex items-center gap-1.5 rounded-full border px-3 py-1 text-xs font-medium transition-colors";

interface Props {
  selected: ContributionKind;
  onSelect: (kind: ContributionKind) => void;
}

/**
 * A chip row over the contribution-kind vocabulary (All/Talks/Artifacts),
 * matching the look of the Saved page's TypeChips row.
 */
export function KindChips({ selected, onSelect }: Readonly<Props>) {
  return (
    <div className="flex flex-wrap items-center gap-2">
      {KINDS.map((kind) => {
        const active = kind.id === selected;
        return (
          <Button
            key={kind.id}
            variant="ghost"
            size="sm"
            aria-pressed={active}
            onPress={() => onSelect(kind.id)}
            className={cn(
              chipBase,
              active
                ? "border-primary bg-primary text-primary-foreground hover:bg-primary-strong"
                : "border-border bg-surface text-muted-foreground hover:border-border-strong hover:bg-surface hover:text-foreground shadow-sm",
            )}
          >
            {kind.label}
          </Button>
        );
      })}
    </div>
  );
}
