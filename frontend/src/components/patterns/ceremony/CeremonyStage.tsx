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
import type { Painter } from "./paint";
import { viewportField } from "./paint";
import { paintFrame, placeCard, revealer, stagedSize } from "./stage-frame";
import { centredPlacement, SETTLE_SECONDS, settlePhase } from "./stage-motion";
import { StageBackdrop, useStageBackdrop } from "./StageBackdrop";

const SLOW_FRAME_MS = 50;
const SLOW_FRAMES_TO_FINISH = 3;
const WARMUP_MS = 250;

export interface CeremonyStageProps {
  /** Seconds of choreography before the stage settles. */
  length: number;
  /** The on-page element the stage card settles onto. It must be mounted before the stage commits, so render it earlier in the tree. */
  target: RefObject<HTMLElement | null>;
  /** Morph onto the target and cross-fade into it, or fade out in place where the target is not the same card. */
  settle: "morph" | "fade";
  /** Seconds the settle takes after `length`. */
  settleSeconds?: number;
  onFrame: (card: HTMLElement, t: number) => void;
  paint?: Painter;
  onDone: () => void;
  clock?: CeremonyClock;
  children: ReactNode;
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
  backdrop: RefObject<HTMLElement | null>;
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
  const { length, settleSeconds = SETTLE_SECONDS } = props.current;
  const size = stagedSize(card, box);
  const origin = {
    from: centredPlacement(size, viewportField()),
    width: box.width,
  };
  const target = revealer(props.current.target);
  const start = clock.now();
  let last = start;
  let slow = 0;
  let handle = 0;

  const draw = (t: number) => {
    props.current.onFrame(card, Math.min(t, length));
    const phase = settlePhase(t, { length, settleSeconds });
    placeCard(card, props.current, origin, phase);
    if (props.current.settle === "morph" && phase.crossfade > 0) {
      target.reveal();
    }
    backdrop.current?.style.setProperty("opacity", String(1 - phase.settled));
    paintFrame(canvas, props.current.paint, t, card);
  };

  const tick = () => {
    const now = clock.now();
    const warm = now - start >= WARMUP_MS;
    slow = warm && now - last > SLOW_FRAME_MS ? slow + 1 : 0;
    last = now;
    const t = (now - start) / 1000;
    if (slow >= SLOW_FRAMES_TO_FINISH || t >= length + settleSeconds) {
      finish();
      return;
    }
    draw(t);
    handle = clock.request(tick);
  };

  draw(0);
  handle = clock.request(tick);
  return () => {
    clock.cancel(handle);
    target.restore();
  };
}

function Stage({
  backdrop,
  ...props
}: Readonly<CeremonyStageProps & { backdrop: RefObject<HTMLElement | null> }>) {
  const { target, clock = browserClock, children } = props;
  const card = useRef<HTMLDivElement>(null);
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
      backdrop,
      canvas: canvas.current,
      box,
      props: latest,
      clock,
      finish,
    });
  }, [backdrop, clock, finish, latest, target]);

  useEffect(() => {
    const skip = (event: KeyboardEvent) => {
      if (event.ctrlKey || event.metaKey) {
        return;
      }
      event.preventDefault();
      event.stopPropagation();
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
      <div
        ref={card}
        data-ceremony-card
        inert
        className="theme-dark text-foreground absolute top-0 left-0 origin-top-left"
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

const REDUCED_FADE = { duration: 200, easing: "ease-out" };

/** No stage: the target simply fades in, with no movement. */
function ReducedMotion({
  target,
  onDone,
}: Readonly<Pick<CeremonyStageProps, "target" | "onDone">>) {
  const finish = useFinish(onDone);
  useEffect(() => {
    target.current?.animate?.([{ opacity: 0 }, { opacity: 1 }], REDUCED_FADE);
    finish();
  }, [finish, target]);
  return null;
}

/** The stage stands on the backdrop its consumer holds up, or brings its own. */
function OnBackdrop(props: Readonly<CeremonyStageProps>) {
  const backdrop = useStageBackdrop();
  return backdrop === null ? (
    <StageBackdrop>
      <OnBackdrop {...props} />
    </StageBackdrop>
  ) : (
    <Stage {...props} backdrop={backdrop} />
  );
}

/**
 * A full-screen moment that plays `onFrame`'s choreography over a dark backdrop, then settles
 * onto `target`. Any click or key finishes it at once, and it never plays under reduced motion.
 * The staged card always wears the dark theme: sparks and sheen only read on dark.
 */
export function CeremonyStage(props: Readonly<CeremonyStageProps>) {
  const reduced = useMediaQuery("(prefers-reduced-motion: reduce)");
  return reduced ? (
    <ReducedMotion target={props.target} onDone={props.onDone} />
  ) : (
    <OnBackdrop {...props} />
  );
}
