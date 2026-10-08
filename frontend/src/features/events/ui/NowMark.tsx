import type { CSSProperties } from "react";

import type { NowMarker } from "@/features/events/domain/now-marker";

/**
 * Where the mark sits in its row: the hour it is now, kept clear of the block start times that
 * share the gutter column. Without the clamp a mark near a block's own start paints over that
 * start, and one at the very top of the sheet hangs half outside it.
 */
const AT = "top-[clamp(2rem,var(--at),calc(100%_-_2rem))]";

/**
 * How far a running session has got, drawn on the rail rather than across the sheet.
 *
 * A line spanning the agenda would be the fourth horizontal rule on a page that already has a
 * section rule, a used-minutes meter and the planned-end marker, so the position is carried by
 * the accent rail the sheet already draws beside every block: faded behind the mark, full ahead
 * of it. The hour sits in the gutter column, on the same edge as the block start times, where it
 * can never land on top of the agenda.
 *
 * Both parts are placed against the row rather than the sheet, because a row is as tall as its
 * minutes or as tall as its contents, whichever is more.
 */
export function NowMark({ marker }: Readonly<{ marker: NowMarker }>) {
  const at = {
    "--at": `${(marker.fraction * 100).toFixed(1)}%`,
  } as CSSProperties;
  return (
    <>
      <span
        data-testid="now-marker"
        className={`absolute left-0 w-13 -translate-y-1/2 text-right max-sm:w-10 ${AT}`}
        style={at}
      >
        <span className="bg-primary text-primary-foreground rounded-full px-1.5 py-0.5 text-xs font-bold tabular-nums">
          <span className="sr-only">Now, </span>
          {marker.label}
        </span>
      </span>
      <span
        aria-hidden
        className={`timeline-beacon bg-primary outline-surface absolute left-[calc(3.25rem+0.875rem+2px)] size-2.5 -translate-x-1/2 -translate-y-1/2 rounded-full outline-3 max-sm:left-[calc(2.5rem+0.5rem+2px)] ${AT}`}
        style={at}
      />
    </>
  );
}
