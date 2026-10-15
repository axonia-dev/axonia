import { useEffect, useMemo, useRef } from "react";

import {
  browserClock,
  type CeremonyClock,
} from "@/components/patterns/ceremony/clock";
import { canvasColour } from "@/components/patterns/ceremony/paint";
import { cn } from "@/lib/cn";
import { useMediaQuery } from "@/lib/use-media-query";

import { AMBIENT_MOTES, buildMotes, type Light, moteAt } from "./mote-field";
import {
  type Ink,
  type InkKind,
  inkOf,
  paintFrame,
  rgbOf,
} from "./paint-motes";

export interface AmbientFieldProps {
  readonly lights: readonly Light[];
  readonly seed: number;
  readonly hue: string;
  readonly inks: Readonly<{ upper: InkKind; lower: InkKind }>;
  readonly seam?: (host: HTMLElement) => number;
  readonly clock?: CeremonyClock;
}

const MAX_DPR = 2;
const SLOW_FRAME_MS = 50;
const SLOW_FRAMES_TO_STOP = 5;
const NO_SEAM = () => 0;

interface Loop {
  readonly draw: (seconds: number) => void;
  readonly clock: CeremonyClock;
}

/** Runs `draw` every frame until stopped, paused, or slowed down for too long. */
function runLoop({ draw, clock }: Loop) {
  let handle: number | null = null;
  let last = clock.now();
  let slow = 0;
  let stopped = false;
  const tick = () => {
    const now = clock.now();
    slow = now - last > SLOW_FRAME_MS ? slow + 1 : 0;
    last = now;
    draw(now / 1000);
    handle = slow >= SLOW_FRAMES_TO_STOP ? null : clock.request(tick);
    stopped = handle === null;
  };
  const pause = () => {
    if (handle !== null) clock.cancel(handle);
    handle = null;
  };
  const resume = () => {
    if (handle === null && !stopped) {
      last = clock.now();
      handle = clock.request(tick);
    }
  };
  return { pause, resume, start: resume };
}

function resolveInks(canvas: HTMLCanvasElement, props: AmbientFieldProps) {
  const accent = rgbOf(canvasColour(props.hue, canvas.parentElement ?? canvas));
  const upper: Ink = inkOf(props.inks.upper, accent);
  const lower: Ink = inkOf(props.inks.lower, accent);
  return { upper, lower };
}

function watchVisibility(
  canvas: HTMLCanvasElement,
  loop: { pause(): void; resume(): void },
) {
  const onVisibility = () =>
    document.visibilityState === "hidden" ? loop.pause() : loop.resume();
  const observer = new IntersectionObserver(([entry]) =>
    entry?.isIntersecting === false ? loop.pause() : loop.resume(),
  );
  observer.observe(canvas);
  document.addEventListener("visibilitychange", onVisibility);
  return () => {
    observer.disconnect();
    document.removeEventListener("visibilitychange", onVisibility);
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
    const host = canvas.parentElement ?? canvas;
    const scratch = document.createElement("canvas");
    const ink = resolveInks(canvas, { lights, seed, hue, inks });
    const draw = (seconds: number) => {
      const box = { width: host.clientWidth, height: host.clientHeight };
      const ratio = Math.min(MAX_DPR, window.devicePixelRatio || 1);
      canvas.width = Math.round(box.width * ratio);
      canvas.height = Math.round(box.height * ratio);
      context.setTransform(ratio, 0, 0, ratio, 0, 0);
      context.clearRect(0, 0, box.width, box.height);
      const samples = motes.map((mote) => moteAt(mote, seconds, box));
      paintFrame(context, {
        samples,
        box,
        seam: seam(host),
        ...ink,
        scratch,
        ratio,
      });
    };
    draw(clock.now() / 1000);
    canvas.dataset.painted = "true";
    if (reduced) return;
    const loop = runLoop({ draw, clock });
    const unwatch = watchVisibility(canvas, loop);
    loop.start();
    return () => {
      loop.pause();
      unwatch();
    };
  }, [motes, reduced, clock, seam, hue, inks, lights, seed]);

  return (
    <canvas
      ref={canvasRef}
      data-ambient-field
      aria-hidden="true"
      className={cn(
        "pointer-events-none absolute inset-0 size-full opacity-0 transition-opacity duration-700",
        "data-[painted=true]:opacity-100",
      )}
    />
  );
}
