import type { Box, MoteSample } from "./mote-field";

export type InkKind = "accent" | "light";

type Rgb = readonly [number, number, number];

export interface Ink {
  readonly rgb: Rgb;
  readonly additive: boolean;
}

export interface Frame {
  readonly samples: readonly MoteSample[];
  readonly box: Box;
  readonly seam: number;
  readonly upper: Ink;
  readonly lower: Ink;
  /** Sized to the canvas by its owner; cleared, never resized, per frame. */
  readonly scratch: HTMLCanvasElement | null;
  readonly ratio: number;
}

const HALO = 3;
const CROSSFADE = 24;
const LIGHTEN = 0.7;

export function rgbOf(cssColour: string): [number, number, number] {
  const match = /rgba?\(\s*(\d+)[,\s]+(\d+)[,\s]+(\d+)/.exec(cssColour);
  if (!match) {
    return [0, 0, 0];
  }
  return [Number(match[1]), Number(match[2]), Number(match[3])];
}

export function inkOf(kind: InkKind, accent: Rgb): Ink {
  if (kind === "accent") {
    return { rgb: [...accent], additive: false };
  }
  const lift = (channel: number) =>
    Math.round(channel + (255 - channel) * LIGHTEN);
  return {
    rgb: [lift(accent[0]), lift(accent[1]), lift(accent[2])],
    additive: true,
  };
}

const tint = (ink: Ink, alpha: number) =>
  `rgba(${ink.rgb[0]}, ${ink.rgb[1]}, ${ink.rgb[2]}, ${Math.max(0, alpha)})`;

const core = (ink: Ink, alpha: number) =>
  ink.additive
    ? `rgba(255, 255, 255, ${Math.min(0.9, alpha)})`
    : tint(ink, Math.min(0.85, alpha * 1.1));

function drawMote(
  context: CanvasRenderingContext2D,
  sample: MoteSample,
  ink: Ink,
) {
  const reach = sample.radius * HALO;
  const halo = context.createRadialGradient(
    sample.x,
    sample.y,
    0,
    sample.x,
    sample.y,
    reach,
  );
  halo.addColorStop(0, tint(ink, sample.alpha));
  halo.addColorStop(0.3, tint(ink, sample.alpha * 0.5));
  halo.addColorStop(1, tint(ink, 0));
  context.fillStyle = halo;
  context.beginPath();
  context.arc(sample.x, sample.y, reach, 0, Math.PI * 2);
  context.fill();
  context.fillStyle = core(ink, sample.alpha);
  context.beginPath();
  context.arc(sample.x, sample.y, sample.radius * 0.4, 0, Math.PI * 2);
  context.fill();
}

function drawMotes(
  context: CanvasRenderingContext2D,
  samples: readonly MoteSample[],
  ink: Ink,
) {
  context.globalCompositeOperation = ink.additive ? "lighter" : "source-over";
  for (const sample of samples) {
    drawMote(context, sample, ink);
  }
  context.globalCompositeOperation = "source-over";
}

function mask(
  context: CanvasRenderingContext2D,
  frame: Frame,
  keepUpper: boolean,
) {
  const gradient = context.createLinearGradient(
    0,
    frame.seam - CROSSFADE,
    0,
    frame.seam + CROSSFADE,
  );
  gradient.addColorStop(0, keepUpper ? "rgba(0,0,0,1)" : "rgba(0,0,0,0)");
  gradient.addColorStop(1, keepUpper ? "rgba(0,0,0,0)" : "rgba(0,0,0,1)");
  context.globalCompositeOperation = "destination-in";
  context.fillStyle = gradient;
  context.fillRect(0, 0, frame.box.width, frame.box.height);
  context.globalCompositeOperation = "source-over";
}

function layer(
  context: CanvasRenderingContext2D,
  frame: Frame,
  upper: boolean,
) {
  const scratch = frame.scratch;
  const layerContext = scratch?.getContext("2d");
  if (!scratch || !layerContext) {
    return;
  }
  const ink = upper ? frame.upper : frame.lower;
  layerContext.setTransform(frame.ratio, 0, 0, frame.ratio, 0, 0);
  layerContext.clearRect(0, 0, frame.box.width, frame.box.height);
  drawMotes(layerContext, frame.samples, ink);
  mask(layerContext, frame, upper);
  context.save();
  context.setTransform(1, 0, 0, 1, 0, 0);
  context.globalCompositeOperation = ink.additive ? "lighter" : "source-over";
  context.drawImage(scratch, 0, 0);
  context.restore();
}

/** Full strength over the banner, easing away behind the text. */
function fadeDown(context: CanvasRenderingContext2D, box: Box, seam: number) {
  const edge = seam / box.height;
  const gradient = context.createLinearGradient(0, 0, 0, box.height);
  gradient.addColorStop(0, "rgba(0,0,0,1)");
  gradient.addColorStop(Math.min(1, edge + 0.04), "rgba(0,0,0,1)");
  gradient.addColorStop(Math.min(1, edge + 0.28), "rgba(0,0,0,0.4)");
  gradient.addColorStop(1, "rgba(0,0,0,0.14)");
  context.globalCompositeOperation = "destination-in";
  context.fillStyle = gradient;
  context.fillRect(0, 0, box.width, box.height);
  context.globalCompositeOperation = "source-over";
}

const sameInk = (left: Ink, right: Ink) =>
  left.additive === right.additive && left.rgb.join() === right.rgb.join();

export function paintFrame(
  context: CanvasRenderingContext2D,
  frame: Frame,
): void {
  if (sameInk(frame.upper, frame.lower) || !frame.scratch) {
    drawMotes(context, frame.samples, frame.lower);
  } else {
    layer(context, frame, true);
    layer(context, frame, false);
  }
  fadeDown(context, frame.box, frame.seam);
}
