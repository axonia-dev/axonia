import { Button } from "@/components/ui/Button";
import { Select } from "@/components/ui/Select";

interface Props {
  selected: string[];
  survivor: string;
  onSurvivorChange: (tag: string) => void;
  onMerge: () => void;
  onClear: () => void;
}

const chip =
  "inline-flex items-center rounded-full border px-3 py-1 text-sm font-semibold";
const mergedChip = `${chip} bg-surface border-border-strong text-foreground`;
const survivorChip = `${chip} border-primary text-primary-strong bg-[color-mix(in_srgb,var(--primary)_12%,var(--surface))]`;

/**
 * Sticky action bar shown while ≥2 tags are selected. It spells out the merge
 * direction, "Merge <tags> → <survivor>", so the surviving name is never
 * ambiguous. With exactly two tags a swap button flips which one survives;
 * with three or more the survivor is a dropdown (a swap can't express it).
 */
export function BatchMergeBar({
  selected,
  survivor,
  onSurvivorChange,
  onMerge,
  onClear,
}: Readonly<Props>) {
  const merged = selected.filter((t) => t !== survivor);
  const isPair = selected.length === 2;
  return (
    <div className="bg-surface-sunken border-border flex flex-wrap items-center justify-between gap-3 rounded-xl border px-4 py-3">
      <div className="flex flex-wrap items-center gap-2 text-sm">
        <span className="text-muted-foreground">Merge</span>
        {merged.map((tag) => (
          <span key={tag} className={mergedChip}>
            {tag}
          </span>
        ))}
        <span aria-hidden className="text-muted-foreground text-base">
          →
        </span>
        {isPair ? (
          <>
            <span className={survivorChip}>{survivor}</span>
            <button
              type="button"
              aria-label="Swap which tag survives"
              onClick={() => merged[0] && onSurvivorChange(merged[0])}
              className="border-border-strong text-muted-foreground hover:border-primary hover:text-primary flex size-[30px] items-center justify-center rounded-md border bg-[var(--surface)] text-sm"
            >
              ⇄
            </button>
          </>
        ) : (
          <Select
            label="Surviving tag"
            hideLabel
            value={survivor}
            options={selected.map((t) => ({ id: t, label: t }))}
            onChange={onSurvivorChange}
          />
        )}
      </div>
      <div className="flex gap-2">
        <Button variant="outline" onPress={onClear}>
          Clear
        </Button>
        <Button variant="primary" onPress={onMerge}>
          {merged.length === 1 ? "Merge" : `Merge ${merged.length}`}
        </Button>
      </div>
    </div>
  );
}
