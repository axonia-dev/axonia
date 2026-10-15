import { useEffect, useMemo, useRef } from "react";

import {
  browserClock,
  type CeremonyClock,
} from "@/components/patterns/ceremony/clock";
import { canvasColour } from "@/components/patterns/ceremony/paint";
import { cn } from "@/lib/cn";
import { useMediaQuery } from "@/lib/use-media-query";

import { runLoop, watchVisibility } from "./field-loop";
import {
  AMBIENT_MOTES,
  type Box,
  buildMotes,
  type Light,
  type Mote,
  moteAt,
} from "./mote-field";
import {
  type Ink,
  type InkKind,
  inkOf,
  paintFrame,
  rgbOf,
} from "./paint-motes";

type Inks = Readonly<{ upper: InkKind; lower: InkKind }>;
type SeamOf = (host: HTMLElement) => number;

export interface AmbientFieldProps {
  readonly lights: readonly Light[];
  readonly seed: number;
  readonly hue: string;
  readonly inks: Inks;
  readonly seam?: SeamOf;
  readonly clock?: CeremonyClock;
}

const MAX_DPR = 2;
const NO_SEAM = () => 0;

interface Geometry {
  readonly box: Box;
  readonly ratio: number;
  readonly seam: number;
}

interface Field {
  readonly canvas: HTMLCanvasElement;
  readonly context: CanvasRenderingContext2D;
  readonly motes: readonly Mote[];
  readonly ink: Readonly<{ upper: Ink; lower: Ink }>;
  readonly seam: SeamOf;
  readonly clock: CeremonyClock;
  readonly reduced: boolean;
}

function resolveInks(canvas: HTMLCanvasElement, hue: string, inks: Inks) {
  const accent = rgbOf(canvasColour(hue, canvas.parentElement ?? canvas));
  return { upper: inkOf(inks.upper, accent), lower: inkOf(inks.lower, accent) };
}

const sameBox = (left: Box, right: Box) =>
  left.width === right.width && left.height === right.height;

/** Sizes the backing stores to the host, at most at twice the CSS pixels. */
function fit(
  canvases: readonly HTMLCanvasElement[],
  host: HTMLElement,
  seam: SeamOf,
): Geometry {
  const box = { width: host.clientWidth, height: host.clientHeight };
  const ratio = Math.min(MAX_DPR, window.devicePixelRatio || 1);
  for (const canvas of canvases) {
    canvas.width = Math.round(box.width * ratio);
    canvas.height = Math.round(box.height * ratio);
  }
  return { box, ratio, seam: seam(host) };
}

/** Resolves the hidden style first, so setting the flag always runs the fade-in. */
function reveal(canvas: HTMLCanvasElement) {
  getComputedStyle(canvas).getPropertyValue("opacity");
  canvas.dataset.painted = "true";
}

function startField(field: Field): () => void {
  const { canvas, context, clock } = field;
  const host = canvas.parentElement ?? canvas;
  const scratch = document.createElement("canvas");
  const canvases = [canvas, scratch];
  let geometry = fit(canvases, host, field.seam);
  let shown = clock.now() / 1000;
  const draw = (seconds: number) => {
    shown = seconds;
    const { box, ratio, seam } = geometry;
    context.setTransform(ratio, 0, 0, ratio, 0, 0);
    context.clearRect(0, 0, box.width, box.height);
    const samples = field.motes.map((mote) => moteAt(mote, seconds, box));
    paintFrame(context, { samples, box, seam, ...field.ink, scratch, ratio });
  };
  // Resizing clears the bitmap, so the frame on show is drawn again at once, running or not.
  const resized = new ResizeObserver(() => {
    const box = { width: host.clientWidth, height: host.clientHeight };
    if (sameBox(box, geometry.box)) return;
    geometry = fit(canvases, host, field.seam);
    draw(shown);
  });
  draw(shown);
  reveal(canvas);
  resized.observe(host);
  const unwatch = field.reduced
    ? () => undefined
    : watchVisibility(canvas, runLoop(draw, clock));
  return () => {
    unwatch();
    resized.disconnect();
  };
}

export function AmbientField(props: Readonly<AmbientFieldProps>) {
  const canvasRef = useRef<HTMLCanvasElement>(null);
  const reduced = useMediaQuery("(prefers-reduced-motion: reduce)");
  const {
    lights,
    seed,
    hue,
    inks,
    seam = NO_SEAM,
    clock = browserClock,
  } = props;
  const motes = useMemo(
    () => buildMotes({ lights, ambient: AMBIENT_MOTES, seed }),
    [lights, seed],
  );

  useEffect(() => {
    const canvas = canvasRef.current;
    const context = canvas?.getContext("2d");
    if (!canvas || !context) return;
    const ink = resolveInks(canvas, hue, inks);
    return startField({ canvas, context, motes, ink, seam, clock, reduced });
  }, [motes, reduced, clock, seam, hue, inks]);

  return (
    <canvas
      ref={canvasRef}
      data-ambient-field
      aria-hidden="true"
      className={cn(
        "pointer-events-none absolute inset-0 z-[1] size-full opacity-0 transition-opacity duration-600",
        "data-[painted=true]:opacity-100",
      )}
    />
  );
}
