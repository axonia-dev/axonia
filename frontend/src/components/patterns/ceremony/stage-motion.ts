import { easeInOutCubic, lerp } from "./timeline";

export const SETTLE_SECONDS = 0.85;
const ENTRANCE_SECONDS = 0.18;
const CROSSFADE_SECONDS = 0.25;
const MARGIN = 24;
const MAX_SCALE = 2;

export interface StagePlacement {
  readonly x: number;
  readonly y: number;
  readonly scale: number;
}

export interface Size {
  readonly width: number;
  readonly height: number;
}

export function centredPlacement(card: Size, viewport: Size): StagePlacement {
  const scale = Math.min(
    MAX_SCALE,
    (viewport.width - 2 * MARGIN) / card.width,
    (viewport.height - 2 * MARGIN) / card.height,
  );
  return {
    x: (viewport.width - card.width * scale) / 2,
    y: (viewport.height - card.height * scale) / 2,
    scale,
  };
}

export function between(
  from: StagePlacement,
  to: StagePlacement,
  p: number,
): StagePlacement {
  return {
    x: lerp(from.x, to.x, p),
    y: lerp(from.y, to.y, p),
    scale: lerp(from.scale, to.scale, p),
  };
}

export interface SettleTiming {
  readonly length: number;
  readonly settleSeconds: number;
}

/** Where the stage stands at `t`: each share runs from 0 to 1. */
export interface SettlePhase {
  /** The card's way from the centre onto the target, eased. */
  readonly travel: number;
  /** The staged card handing over to the real one, once it has arrived. */
  readonly crossfade: number;
  /** The whole settle, eased: the page fading back in around the card. */
  readonly settled: number;
  readonly backdrop: number;
}

function share(elapsed: number, seconds: number): number {
  return Math.min(1, Math.max(0, elapsed / seconds));
}

export function settlePhase(
  t: number,
  { length, settleSeconds }: SettleTiming,
): SettlePhase {
  const crossfadeSeconds = Math.min(CROSSFADE_SECONDS, settleSeconds / 2);
  const travelSeconds = settleSeconds - crossfadeSeconds;
  const settled = easeInOutCubic(share(t - length, settleSeconds));
  return {
    travel: easeInOutCubic(share(t - length, travelSeconds)),
    crossfade: share(t - length - travelSeconds, crossfadeSeconds),
    settled,
    backdrop: Math.min(share(t, ENTRANCE_SECONDS), 1 - settled),
  };
}
