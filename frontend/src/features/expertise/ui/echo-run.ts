import type { CeremonyClock } from "@/components/patterns/ceremony/clock";

/** One frame per animation frame for a fixed length, ending on exactly that length so the last frame is the settled one. */
export function runFor({
  clock,
  lengthMs,
  step,
}: Readonly<{
  clock: CeremonyClock;
  lengthMs: number;
  step: (elapsedMs: number) => void;
}>): () => void {
  const start = clock.now();
  let handle = 0;
  let live = true;
  const frame = () => {
    if (!live) return;
    const elapsed = Math.min(lengthMs, clock.now() - start);
    step(elapsed);
    if (elapsed < lengthMs) handle = clock.request(frame);
  };
  frame();
  return () => {
    live = false;
    clock.cancel(handle);
  };
}
