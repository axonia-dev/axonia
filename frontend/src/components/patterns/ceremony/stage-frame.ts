import type { RefObject } from "react";

import { type Painter, viewportField } from "./paint";
import { between, type SettlePhase, type StagePlacement } from "./stage-motion";
import { lerp } from "./timeline";

export interface Settling {
  readonly settle: "morph" | "fade";
  readonly target: RefObject<HTMLElement | null>;
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

export function paintFrame(
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

/** Lays the stage card out at the target's width and answers its own size, which can differ from the target's. */
export function stagedSize(card: HTMLElement, box: DOMRect) {
  card.style.width = `${box.width}px`;
  return {
    width: card.offsetWidth || box.width,
    height: card.offsetHeight || box.height,
  };
}

export interface Origin {
  readonly from: StagePlacement;
  readonly width: number;
}

export function placeCard(
  card: HTMLElement,
  { settle, target }: Settling,
  { from, width }: Origin,
  phase: SettlePhase,
): void {
  const to = settle === "morph" ? placementOf(target.current, from) : from;
  const at = between(from, to, phase.travel);
  card.style.width = `${lerp(width, widthOf(target.current, width), phase.travel)}px`;
  card.style.transform = `translate(${at.x}px, ${at.y}px) scale(${at.scale})`;
  card.style.opacity = String(
    settle === "morph" ? 1 - phase.crossfade : 1 - phase.settled,
  );
}

/** The real card shows under the staged one for the closing cross-fade, so the page's own theme fades in without a flash. */
export function revealer(target: RefObject<HTMLElement | null>) {
  let revealed: { element: HTMLElement; visibility: string } | null = null;
  return {
    reveal(): void {
      const element = target.current;
      if (revealed === null && element !== null) {
        revealed = { element, visibility: element.style.visibility };
        element.style.visibility = "visible";
      }
    },
    restore(): void {
      revealed?.element.style.setProperty("visibility", revealed.visibility);
    },
  };
}

const BAIL_FADE: KeyframeAnimationOptions = {
  duration: 250,
  easing: "ease-out",
  fill: "forwards",
};

/** Fades the stage away on the compositor, which keeps running where the main thread cannot keep up. */
export function fadeAway(
  elements: readonly (HTMLElement | null)[],
  done: () => void,
): void {
  const fades = elements.flatMap((element) => {
    const fade = element?.animate?.([{ opacity: 0 }], BAIL_FADE);
    return fade === undefined ? [] : [fade.finished];
  });
  if (fades.length === 0) {
    done();
    return;
  }
  void Promise.all(fades).then(done, done);
}

/** The stage layer's clip onto the target card, rounded like the card. */
export function clipTo(box: DOMRect, element: HTMLElement | null): string {
  const field = viewportField();
  const radius = element === null ? "" : getComputedStyle(element).borderRadius;
  return `inset(${box.top}px ${field.width - box.right}px ${field.height - box.bottom}px ${box.left}px round ${radius || "0px"})`;
}
