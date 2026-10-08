import type { ExpertiseArea } from "@/features/expertise/domain/expertise";
import { plural } from "@/lib/plural";

interface Props {
  areas: ExpertiseArea[];
  onSelect: (tag: string) => void;
  activeTag?: string | undefined;
}

/**
 * The community's knowledge as a word-cloud: each topic sized by how many
 * people can be asked about it. The figure is printed beside the tag as well,
 * since five declarers are drawn as large as five practitioners and only
 * the number keeps that honest. It is muted and out of the accessible name,
 * which already carries it. The cloud is a persistent map: the searched topic
 * stays underlined, so a reader pivots to another in one tap.
 */
function ratioOf(count: number, min: number, max: number): number {
  return max <= min ? 0.5 : (count - min) / (max - min);
}

function sizeClass(ratio: number): string {
  if (ratio >= 0.8) return "text-4xl";
  if (ratio >= 0.6) return "text-3xl";
  if (ratio >= 0.4) return "text-2xl";
  if (ratio >= 0.2) return "text-lg";
  return "text-sm";
}

function colorClass(ratio: number, active: boolean): string {
  if (active || ratio >= 0.8) return "text-primary";
  if (ratio < 0.2) return "text-muted-foreground";
  return "text-foreground";
}

export function TagConstellation({
  areas,
  onSelect,
  activeTag,
}: Readonly<Props>) {
  const counts = areas.map((a) => a.peopleCount);
  const min = Math.min(...counts);
  const max = Math.max(...counts);

  return (
    <div className="flex flex-wrap items-baseline gap-x-4 gap-y-3">
      {areas.map((area) => {
        const ratio = ratioOf(area.peopleCount, min, max);
        const active = area.canonicalTag === activeTag;
        const people = plural(area.peopleCount, "person");
        return (
          <button
            key={area.canonicalTag}
            type="button"
            aria-pressed={active}
            onClick={() => onSelect(area.canonicalTag)}
            title={`${people} can be asked`}
            aria-label={`${area.canonicalTag}, ${people}`}
            className={`font-display hover:text-primary cursor-pointer leading-none font-semibold transition-colors ${sizeClass(ratio)} ${colorClass(ratio, active)} ${active ? "underline decoration-[color-mix(in_srgb,var(--primary)_45%,transparent)] decoration-2 underline-offset-4" : ""}`}
          >
            {area.canonicalTag}
            {/* No whitespace before the count: the pair then has no break opportunity. */}
            <span
              aria-hidden="true"
              className="text-muted-foreground ml-1.5 inline-block align-baseline text-xs font-medium tabular-nums no-underline"
            >
              {area.peopleCount}
            </span>
          </button>
        );
      })}
    </div>
  );
}
