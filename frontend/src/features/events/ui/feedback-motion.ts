import { riseMotes } from "@/features/events/ui/rising-motes";

export const CASCADE_STEP_MS = 24;
const SPRING = "cubic-bezier(.34, 1.7, .64, 1)";

function motesFor(value: number): number {
  if (value === 10) return 14;
  return value === 9 ? 9 : 5;
}

function canAnimate(element: HTMLElement | null): element is HTMLElement {
  return element !== null && typeof element.animate === "function";
}

export function cascade(bars: ReadonlyArray<HTMLElement | null>, upTo: number) {
  bars.slice(0, upTo).forEach((bar, index) => {
    if (!canAnimate(bar)) return;
    bar.dataset.cascade = "";
    bar.animate(
      [
        { transform: "scale(1)" },
        { transform: "scaleY(1.22) scaleX(1.08)", offset: 0.5 },
        { transform: "scale(1)" },
      ],
      { duration: 340, delay: index * CASCADE_STEP_MS, easing: SPRING },
    ).onfinish = () => {
      delete bar.dataset.cascade;
    };
  });
}

export function wiggle(icon: HTMLElement | null) {
  if (!canAnimate(icon)) return;
  icon.animate(
    [
      { transform: "none" },
      { transform: "rotate(-12deg) scale(1.2)", offset: 0.3 },
      { transform: "rotate(9deg) scale(1.15)", offset: 0.6 },
      { transform: "none" },
    ],
    { duration: 500, easing: "ease" },
  );
}

export function motesFromBar(bar: HTMLElement | null, value: number) {
  if (!bar?.isConnected) return;
  const host = bar.closest<HTMLElement>("[data-motes-host]");
  if (!host) return;
  const from = bar.getBoundingClientRect();
  const within = host.getBoundingClientRect();
  riseMotes(
    host,
    from.left - within.left + from.width / 2,
    from.top - within.top,
    motesFor(value),
  );
}

export function rollIn(word: HTMLElement | null) {
  if (!canAnimate(word)) return;
  word.animate(
    [{ transform: "translateY(100%)" }, { transform: "translateY(0)" }],
    { duration: 280, easing: "cubic-bezier(.34, 1.4, .64, 1)" },
  );
}

export function rollOut(word: HTMLElement | null) {
  if (!canAnimate(word)) return;
  word.animate(
    [
      { transform: "translateY(0)", opacity: 1 },
      { transform: "translateY(-100%)", opacity: 0 },
    ],
    { duration: 280, easing: "ease-out", fill: "forwards" },
  );
}

export function squeeze(card: HTMLElement | null) {
  if (!canAnimate(card)) return;
  card.animate(
    [
      { transform: "scale(1)" },
      { transform: "scale(.985)" },
      { transform: "scale(1)" },
    ],
    { duration: 320, easing: "ease-out" },
  );
}

export function settleIn(line: HTMLElement | null) {
  if (!canAnimate(line)) return;
  line.animate(
    [
      { opacity: 0, transform: "translateY(6px)" },
      { opacity: 1, transform: "none" },
    ],
    { duration: 280, easing: "cubic-bezier(.2, .6, .3, 1)" },
  );
}

export function moteFrom(origin: HTMLElement | null, host: HTMLElement | null) {
  if (!origin || !host) return;
  const from = origin.getBoundingClientRect();
  const within = host.getBoundingClientRect();
  riseMotes(
    host,
    from.left - within.left + from.width / 2,
    from.top - within.top,
    1,
  );
}
