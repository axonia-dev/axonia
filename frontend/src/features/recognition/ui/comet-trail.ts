import {
  cometKeyframes,
  type Point,
} from "@/features/recognition/domain/comet-path";

const SIZES = [15, 12, 9, 6] as const;
const STAGGER_MS = 45;
const DURATION_MS = 640;
const EASING = "cubic-bezier(0.65, 0, 0.35, 1)";

export function centreOf(element: Element): Point {
  const r = element.getBoundingClientRect();
  return { x: r.left + r.width / 2, y: r.top + r.height / 2 };
}

export function cometOverlay(): HTMLElement {
  const existing = document.querySelector<HTMLElement>("[data-comet-overlay]");
  if (existing) return existing;
  const overlay = document.createElement("div");
  overlay.dataset.cometOverlay = "";
  overlay.setAttribute("aria-hidden", "true");
  overlay.className =
    "pointer-events-none fixed inset-0 z-[60] overflow-hidden";
  document.body.append(overlay);
  return overlay;
}

function heart(size: number, index: number): HTMLElement {
  const el = document.createElement("span");
  el.textContent = "♥";
  el.setAttribute("aria-hidden", "true");
  el.className =
    "text-primary absolute top-0 left-0 leading-none opacity-0 will-change-transform";
  el.style.fontSize = `${size}px`;
  if (index > 0)
    el.style.color = `color-mix(in srgb, var(--primary) ${100 - index * 22}%, transparent)`;
  return el;
}

export async function flyComet({
  from,
  to,
  delay = 0,
}: Readonly<{ from: Point; to: Point; delay?: number }>): Promise<void> {
  const overlay = cometOverlay();
  const flights = SIZES.map(async (size, index) => {
    const el = heart(size, index);
    if (typeof el.animate !== "function") return;
    overlay.append(el);
    const animation = el.animate(cometKeyframes({ from, to, size }), {
      duration: DURATION_MS,
      delay: delay + index * STAGGER_MS,
      easing: EASING,
      fill: "both",
    });
    await animation.finished.catch(() => undefined);
    el.remove();
  });
  await flights[0];
}
