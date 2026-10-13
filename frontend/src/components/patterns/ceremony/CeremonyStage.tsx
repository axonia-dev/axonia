import {
  type ReactNode,
  type RefObject,
  useCallback,
  useEffect,
  useLayoutEffect,
  useRef,
} from "react";
import { createPortal } from "react-dom";

import { useMediaQuery } from "@/lib/use-media-query";

import { browserClock, type CeremonyClock } from "./clock";
import { type Painter, viewportField } from "./paint";
import { easeInOutCubic, lerp } from "./timeline";

export const SETTLE_SECONDS = 0.85;
const MARGIN = 24;
const MAX_SCALE = 2;
const SLOW_FRAME_MS = 50;
const SLOW_FRAMES_TO_FINISH = 3;
const WARMUP_MS = 250;

export interface StagePlacement {
  readonly x: number;
  readonly y: number;
  readonly scale: number;
}

interface Size {
  readonly width: number;
  readonly height: number;
}

export interface CeremonyStageProps {
  /** Seconds of choreography before the stage settles. */
  length: number;
  /** The on-page element the stage card settles onto. It must be mounted before the stage commits, so render it earlier in the tree. */
  target: RefObject<HTMLElement | null>;
  /** Morph onto the target, or fade out in place where the target is not the same card. */
  settle: "morph" | "fade";
  onFrame: (card: HTMLElement, t: number) => void;
  paint?: Painter;
  onDone: () => void;
  clock?: CeremonyClock;
  children: ReactNode;
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

function between(
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

function placementOf(
  element: HTMLElement | null,
  fallback: StagePlacement,
): StagePlacement {
  const box = element?.getBoundingClientRect();
  return box === undefined ? fallback : { x: box.left, y: box.top, scale: 1 };
}

function widthOf(element: HTMLElement | null, fallback: number): number {
  return element?.getBoundingClientRect().width ?? fallback;
}

function paintFrame(
  canvas: HTMLCanvasElement | null,
  paint: Painter | undefined,
  t: number,
  card: HTMLElement,
): void {
  const context = canvas?.getContext("2d") ?? null;
  if (canvas === null || context === null) {
    return;
  }
  const ratio = window.devicePixelRatio || 1;
  const field = viewportField();
  const width = Math.round(field.width * ratio);
  const height = Math.round(field.height * ratio);
  if (canvas.width !== width || canvas.height !== height) {
    canvas.width = width;
    canvas.height = height;
  }
  context.setTransform(ratio, 0, 0, ratio, 0, 0);
  context.clearRect(0, 0, field.width, field.height);
  paint?.(context, t, card);
}

function useLatest<T>(value: T): RefObject<T> {
  const ref = useRef(value);
  useLayoutEffect(() => {
    ref.current = value;
  });
  return ref;
}

function useFinish(onDone: () => void): () => void {
  const latest = useLatest(onDone);
  const done = useRef(false);
  return useCallback(() => {
    if (!done.current) {
      done.current = true;
      latest.current();
    }
  }, [latest]);
}

interface Playback {
  card: HTMLElement;
  backdrop: HTMLElement | null;
  canvas: HTMLCanvasElement | null;
  box: DOMRect;
  props: RefObject<CeremonyStageProps>;
  clock: CeremonyClock;
  finish: () => void;
}

/** Runs the frame loop; answers the function that stops it. */
function play({
  card,
  backdrop,
  canvas,
  box,
  props,
  clock,
  finish,
}: Playback): () => void {
  const { length, settle } = props.current;
  const from = centredPlacement(box, viewportField());
  const start = clock.now();
  let last = start;
  let slow = 0;
  let handle = 0;
  card.style.width = `${box.width}px`;

  const draw = (t: number) => {
    props.current.onFrame(card, Math.min(t, length));
    const settled = easeInOutCubic(
      Math.min(1, Math.max(0, t - length) / SETTLE_SECONDS),
    );
    const to =
      settle === "morph"
        ? placementOf(props.current.target.current, from)
        : from;
    const at = between(from, to, settled);
    card.style.width = `${lerp(box.width, widthOf(props.current.target.current, box.width), settled)}px`;
    card.style.transform = `translate(${at.x}px, ${at.y}px) scale(${at.scale})`;
    if (settle === "fade") {
      card.style.opacity = String(1 - settled);
    }
    if (backdrop !== null) {
      backdrop.style.opacity = String(1 - settled);
    }
    paintFrame(canvas, props.current.paint, t, card);
  };

  const tick = () => {
    const now = clock.now();
    const warm = now - start >= WARMUP_MS;
    slow = warm && now - last > SLOW_FRAME_MS ? slow + 1 : 0;
    last = now;
    const t = (now - start) / 1000;
    if (slow >= SLOW_FRAMES_TO_FINISH || t >= length + SETTLE_SECONDS) {
      finish();
      return;
    }
    draw(t);
    handle = clock.request(tick);
  };

  draw(0);
  handle = clock.request(tick);
  return () => clock.cancel(handle);
}

function Stage(props: Readonly<CeremonyStageProps>) {
  const { target, clock = browserClock, children } = props;
  const card = useRef<HTMLDivElement>(null);
  const backdrop = useRef<HTMLDivElement>(null);
  const canvas = useRef<HTMLCanvasElement>(null);
  const latest = useLatest(props);
  const finish = useFinish(props.onDone);

  useLayoutEffect(() => {
    const box = target.current?.getBoundingClientRect();
    if (
      card.current === null ||
      box === undefined ||
      box.width === 0 ||
      box.height === 0
    ) {
      finish();
      return undefined;
    }
    return play({
      card: card.current,
      backdrop: backdrop.current,
      canvas: canvas.current,
      box,
      props: latest,
      clock,
      finish,
    });
  }, [clock, finish, latest, target]);

  useEffect(() => {
    const skip = (event: KeyboardEvent) => {
      if (!event.ctrlKey && !event.metaKey) {
        event.preventDefault();
        event.stopPropagation();
      }
      finish();
    };
    window.addEventListener("keydown", skip, true);
    return () => window.removeEventListener("keydown", skip, true);
  }, [finish]);

  return createPortal(
    <div
      data-ceremony-stage
      aria-hidden
      className="fixed inset-0 z-[100] cursor-pointer"
      onClick={finish}
    >
      <div ref={backdrop} className="absolute inset-0 bg-[#0d0910]" />
      <div
        ref={card}
        data-ceremony-card
        inert
        className="absolute top-0 left-0 origin-top-left"
      >
        {children}
      </div>
      <canvas
        ref={canvas}
        className="pointer-events-none absolute inset-0 size-full"
      />
    </div>,
    document.body,
  );
}

function ReducedMotion({ onDone }: Readonly<{ onDone: () => void }>) {
  const finish = useFinish(onDone);
  useEffect(() => {
    finish();
  }, [finish]);
  return null;
}

/**
 * A full-screen moment that plays `onFrame`'s choreography over a dark backdrop, then settles
 * onto `target`. Any click or key finishes it at once, and it never plays under reduced motion.
 */
export function CeremonyStage(props: Readonly<CeremonyStageProps>) {
  const reduced = useMediaQuery("(prefers-reduced-motion: reduce)");
  return reduced ? (
    <ReducedMotion onDone={props.onDone} />
  ) : (
    <Stage {...props} />
  );
}
