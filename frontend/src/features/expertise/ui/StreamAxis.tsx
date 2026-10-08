import { PAD_X } from "@/features/expertise/ui/stream-layout";
import { declaredOn } from "@/lib/declared-on";

/**
 * The stream's only words, and the one thing that turns the bands into a history: which end is old, which is new,
 * and what period the shape covers. It lives in the document rather than in the drawing, because the drawing is
 * `aria-hidden` and text painted into a canvas reaches no screen reader.
 *
 * Its ends are inset by the same {@link PAD_X} the bands are, so each date stands over the end it names, and it
 * rides in the clearance `PAD_Y` already keeps below the stack rather than over the shape. It is handed the span
 * the camera is holding, not the span of the history, so zooming into one season says which season it is.
 */
export function StreamAxis({
  from,
  to,
}: Readonly<{ from: number; to: number }>) {
  // `buildStream` answers 0 to 0 when nothing it was handed carries a readable date, and "1 Jan 1970" is a lie.
  if (from === 0 && to === 0) return null;

  return (
    <p
      className="text-muted-foreground pointer-events-none absolute inset-x-0 bottom-2 flex justify-between text-xs tabular-nums"
      style={{ paddingInline: PAD_X }}
    >
      <span>From {declaredOn(new Date(from).toISOString())}</span>
      <span>To {declaredOn(new Date(to).toISOString())}</span>
    </p>
  );
}
