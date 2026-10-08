import { useId } from "react";

import { EmptyState } from "@/components/patterns/EmptyState";
import type { WantedArea } from "@/features/learning/domain/learning";
import { LearnersList } from "@/features/learning/ui/LearnersList";
import { cn } from "@/lib/cn";

interface Props {
  slug: string;
  /** In the server's order: most learners first. */
  wanted: WantedArea[];
  activeTag: string | null;
  onPick: (tag: string) => void;
}

interface RowProps {
  area: WantedArea;
  active: boolean;
  onPick: (tag: string) => void;
}

function rowName({ tag, learners, canBeAsked }: WantedArea): string {
  const wanting = `${learners} ${learners === 1 ? "wants" : "want"} to learn`;
  const askable = canBeAsked === 0 ? "none" : canBeAsked;
  return `${tag}, ${wanting}, ${askable} can be asked`;
}

/**
 * The area's button stretches over the whole row, so a press anywhere on it picks the area. Its
 * layer behind the cells paints the row's tint as one box: a row background is painted cell by
 * cell, and WebKit leaves a seam where two translucent cells meet on a fractional pixel.
 */
function WantedRow({ area, active, onPick }: Readonly<RowProps>) {
  return (
    <tr className="border-border group relative border-b last:border-b-0">
      <td className="py-2.5 pr-2 pl-3">
        <button
          type="button"
          aria-label={rowName(area)}
          aria-pressed={active}
          onClick={() => onPick(area.tag)}
          className={cn(
            "font-display text-foreground focus-visible:after:ring-focus-ring cursor-pointer text-left font-semibold wrap-anywhere outline-none before:absolute before:inset-0 before:-z-10 before:transition-colors after:absolute after:inset-0 after:rounded-md focus-visible:after:ring-2",
            active
              ? "before:bg-secondary/10"
              : "group-hover:before:bg-surface-sunken",
          )}
        >
          {area.tag}
        </button>
      </td>
      <td className="text-foreground px-2 py-2.5 text-right tabular-nums">
        {area.learners}
      </td>
      <td className="py-2.5 pr-3 pl-2 text-right tabular-nums">
        {area.canBeAsked === 0 ? (
          <span className="text-muted-foreground">none</span>
        ) : (
          <span className="text-foreground">{area.canBeAsked}</span>
        )}
      </td>
    </tr>
  );
}

interface TableProps extends Omit<Props, "slug"> {
  labelledBy: string;
}

function Table({
  wanted,
  activeTag,
  onPick,
  labelledBy,
}: Readonly<TableProps>) {
  return (
    <table
      aria-labelledby={labelledBy}
      className="isolate w-full border-collapse text-sm"
    >
      <thead>
        <tr className="border-border text-muted-foreground border-b text-xs">
          <th scope="col" className="py-2 pr-2 pl-3 text-left font-medium">
            Area
          </th>
          <th scope="col" className="px-2 py-2 text-right font-medium">
            Want to learn
          </th>
          <th scope="col" className="py-2 pr-3 pl-2 text-right font-medium">
            Can be asked
          </th>
        </tr>
      </thead>
      <tbody>
        {wanted.map((area) => (
          <WantedRow
            key={area.tag}
            area={area}
            active={area.tag === activeTag}
            onPick={onPick}
          />
        ))}
      </tbody>
    </table>
  );
}

export function WantedTable({
  slug,
  wanted,
  activeTag,
  onPick,
}: Readonly<Props>) {
  const headingId = useId();
  return (
    <section className="border-border bg-surface flex flex-col gap-3 rounded-2xl border p-4 shadow-sm">
      <h2
        id={headingId}
        className="font-display text-foreground text-base font-semibold"
      >
        Wanted here
      </h2>
      {wanted.length === 0 ? (
        <EmptyState title="No areas wanted yet." className="py-8" />
      ) : (
        <Table
          wanted={wanted}
          activeTag={activeTag}
          onPick={onPick}
          labelledBy={headingId}
        />
      )}
      {activeTag !== null && (
        <div className="border-border border-t pt-4">
          <LearnersList slug={slug} tag={activeTag} />
        </div>
      )}
    </section>
  );
}
