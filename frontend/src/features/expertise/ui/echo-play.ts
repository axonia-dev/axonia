import type { CeremonyClock } from "@/components/patterns/ceremony/clock";
import { blendOf, type Tinted } from "@/features/expertise/ui/echo-blend";
import { type DropSource, dropsFor } from "@/features/expertise/ui/echo-drops";
import type { RingLayer } from "@/features/expertise/ui/echo-rings";
import { runFor } from "@/features/expertise/ui/echo-run";
import {
  amountAt,
  type Bounds,
  type Drop,
  ECHO,
  type Echo,
  echoFor,
  echoLength,
  fadeAmount,
  reachedAt,
  ringsAt,
} from "@/features/expertise/ui/echo-timeline";
import type { DrawnHighlight } from "@/features/expertise/ui/echo-transition";
import type {
  TwinkleLayer,
  TwinklePoint,
} from "@/features/expertise/ui/echo-twinkles";

export interface EchoStage {
  tints: readonly Tinted[];
  /** The marks drawn, in this layer's own coordinates, which the drops and the twinkles are read from. */
  marks: readonly DropSource[];
  bounds: Bounds;
  rings: RingLayer;
  twinkles: (points: readonly TwinklePoint[]) => TwinkleLayer;
  request: () => void;
}

type Amount = (x: number, y: number) => number;

const SETTLED: Amount = () => 1;
const NOTHING_TO_STOP = () => {};

function twinklesOf(
  echo: Echo,
  marks: readonly DropSource[],
  lit: ReadonlySet<number>,
): TwinklePoint[] {
  return marks
    .filter((mark) => lit.has(mark.signalId))
    .map(({ x, y }) => ({ x, y, reachedAtMs: reachedAt(echo, x, y) }));
}

function echoed({
  stage,
  clock,
  drops,
  lit,
  paint,
}: Readonly<{
  stage: EchoStage;
  clock: CeremonyClock;
  drops: readonly Drop[];
  lit: ReadonlySet<number>;
  paint: (amount: Amount) => void;
}>): () => void {
  const echo = echoFor(drops, stage.bounds);
  const lengthMs = echoLength(echo);
  let twinkles: TwinkleLayer | null = stage.twinkles(
    twinklesOf(echo, stage.marks, lit),
  );
  const clear = () => {
    stage.rings.show([]);
    twinkles?.dispose();
    twinkles = null;
  };

  const stop = runFor({
    clock,
    lengthMs,
    step: (ms) => {
      if (ms >= lengthMs) {
        clear();
        paint(SETTLED);
        return;
      }
      stage.rings.show(ringsAt(echo, ms));
      twinkles?.at(ms);
      paint((x, y) => amountAt(echo, x, y, ms));
    },
  });
  return () => {
    stop();
    clear();
    stage.request();
  };
}

/**
 * Takes the map from what it shows now to what a highlight asks for. A snap is painted at once; a fade carries the
 * whole map together; an echo spreads from where the answers are. Every way ends on exactly the new picture, and the
 * cancel it gives back leaves the colours where they got to, for the next one to carry on from.
 */
export function play({
  stage,
  drawn,
  clock,
}: Readonly<{
  stage: EchoStage;
  drawn: DrawnHighlight;
  clock: CeremonyClock;
}>): () => void {
  const blends = stage.tints.map((tint) =>
    blendOf(tint, tint.targetFor(drawn.highlight)),
  );
  const paint = (amount: Amount) => {
    for (const blend of blends) blend.apply(amount);
    stage.request();
  };

  if (drawn.transition === "snap") {
    paint(SETTLED);
    return NOTHING_TO_STOP;
  }
  const lit = drawn.highlight.marks ?? new Set<number>();
  const drops = drawn.transition === "echo" ? dropsFor(stage.marks, lit) : [];
  if (drops.length > 0) return echoed({ stage, clock, drops, lit, paint });

  return runFor({
    clock,
    lengthMs: ECHO.fadeMs,
    step: (ms) => {
      const k = ms >= ECHO.fadeMs ? 1 : fadeAmount(ms);
      paint(() => k);
    },
  });
}
