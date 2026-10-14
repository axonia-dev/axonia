import type { CeremonyClock } from "@/components/patterns/ceremony/clock";
import {
  type Blend,
  blendOf,
  type Tinted,
} from "@/features/expertise/ui/echo-blend";
import { dropsFor, type DropSource } from "@/features/expertise/ui/echo-drops";
import { type EchoField, echoField } from "@/features/expertise/ui/echo-field";
import type { RingLayer } from "@/features/expertise/ui/echo-rings";
import { runFor } from "@/features/expertise/ui/echo-run";
import {
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
  /** Whether anyone can see the map: a frame nobody sees is not worked out, only the settled one at the end. */
  onScreen: () => boolean;
}

interface Painted {
  tint: Tinted;
  blend: Blend;
}

const SETTLED = 1;
const NOTHING_TO_STOP = () => {};

function twinklesOf(
  echo: Echo,
  marks: readonly DropSource[],
  lit: ReadonlySet<number>,
): TwinklePoint[] {
  return marks
    .filter((mark) => lit.has(mark.signalId))
    .map(({ x, y, slot }) => ({
      x,
      y,
      slot,
      reachedAtMs: reachedAt(echo, x, y),
    }));
}

/**
 * One field per set of positions rather than per tint: a mark's glyph and rim share their positions, so the amount
 * at each mark is worked out once a frame and given to both.
 */
function fieldsOf(echo: Echo, painted: readonly Painted[]) {
  const fields = new Map<Float32Array, EchoField>();
  for (const { tint } of painted) {
    if (!fields.has(tint.xy)) fields.set(tint.xy, echoField(echo, tint.xy));
  }
  return fields;
}

function echoed({
  stage,
  clock,
  drops,
  lit,
  painted,
}: Readonly<{
  stage: EchoStage;
  clock: CeremonyClock;
  drops: readonly Drop[];
  lit: ReadonlySet<number>;
  painted: readonly Painted[];
}>): () => void {
  const echo = echoFor(drops, stage.bounds);
  const lengthMs = echoLength(echo);
  const fields = fieldsOf(echo, painted);
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
        for (const { blend } of painted) blend.apply(SETTLED);
        stage.request();
        return;
      }
      if (!stage.onScreen()) return;
      stage.rings.show(ringsAt(echo, ms));
      twinkles?.at(ms);
      for (const field of fields.values()) field.at(ms);
      for (const { tint, blend } of painted) {
        blend.apply(fields.get(tint.xy)!.amounts);
      }
      stage.request();
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
  const painted = stage.tints.map((tint) => ({
    tint,
    blend: blendOf(tint, tint.targetFor(drawn.highlight)),
  }));
  const paint = (amount: number) => {
    for (const { blend } of painted) blend.apply(amount);
    stage.request();
  };

  if (drawn.transition === "snap") {
    paint(SETTLED);
    return NOTHING_TO_STOP;
  }
  const lit = drawn.highlight.marks ?? new Set<number>();
  const drops = drawn.transition === "echo" ? dropsFor(stage.marks, lit) : [];
  if (drops.length > 0) return echoed({ stage, clock, drops, lit, painted });

  return runFor({
    clock,
    lengthMs: ECHO.fadeMs,
    step: (ms) => {
      if (ms < ECHO.fadeMs && !stage.onScreen()) return;
      paint(ms >= ECHO.fadeMs ? SETTLED : fadeAmount(ms));
    },
  });
}
