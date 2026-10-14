export const STAGE_DARK = "#0d0910";

const SELECTOR = "[data-ceremony-curtain]";
const HOLD_MS = 5_000;
const LIFT = { duration: 250, easing: "ease-out" };

function lift(curtain: HTMLElement): void {
  if (!curtain.isConnected || curtain.dataset.ceremonyCurtain === "lifting") {
    return;
  }
  curtain.dataset.ceremonyCurtain = "lifting";
  if (typeof curtain.animate !== "function") {
    curtain.remove();
    return;
  }
  const fade = curtain.animate([{ opacity: 1 }, { opacity: 0 }], LIFT);
  fade.onfinish = () => curtain.remove();
}

/**
 * Darkens the page from the app's first frame, for a ceremony that arrives with a page load, so the
 * page never shows before the stage. A stage backdrop takes it over; when none does in time, it lifts.
 */
export function raiseCurtain(holdMs = HOLD_MS): void {
  if (document.querySelector(SELECTOR) !== null) {
    return;
  }
  const curtain = document.createElement("div");
  curtain.dataset.ceremonyCurtain = "";
  curtain.setAttribute("aria-hidden", "true");
  Object.assign(curtain.style, {
    position: "fixed",
    inset: "0",
    zIndex: "100",
    background: STAGE_DARK,
  });
  document.body.append(curtain);
  setTimeout(() => lift(curtain), holdMs);
}

/** Lifts the curtain at once, for a page that knows no ceremony is coming. */
export function liftCurtain(): void {
  const curtain = document.querySelector<HTMLElement>(SELECTOR);
  if (curtain !== null) {
    lift(curtain);
  }
}

/** Removes the curtain for a backdrop to stand in its place: how dark it still was, 0 when none was up. */
export function takeCurtain(): number {
  const curtain = document.querySelector<HTMLElement>(SELECTOR);
  if (curtain === null) {
    return 0;
  }
  const opacity = Number(getComputedStyle(curtain).opacity);
  curtain.remove();
  return Number.isNaN(opacity) ? 1 : opacity;
}
