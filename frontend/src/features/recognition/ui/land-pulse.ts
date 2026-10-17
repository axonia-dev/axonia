import { cometOverlay } from "@/features/recognition/ui/comet-trail";

const RING_INFLATE_PX = 4;

function bounce(target: HTMLElement): void {
  target.animate(
    [
      { transform: "scale(1)" },
      { transform: "scale(1.18)", offset: 0.4 },
      { transform: "scale(1)" },
    ],
    {
      duration: 380,
      easing: "cubic-bezier(0.3, 1.6, 0.5, 1)",
    },
  );
}

async function ring(target: HTMLElement): Promise<void> {
  const rect = target.getBoundingClientRect();
  const el = document.createElement("span");
  el.setAttribute("aria-hidden", "true");
  el.className = "absolute rounded-full border-2 border-primary";
  el.style.left = `${rect.left - RING_INFLATE_PX}px`;
  el.style.top = `${rect.top - RING_INFLATE_PX}px`;
  el.style.width = `${rect.width + RING_INFLATE_PX * 2}px`;
  el.style.height = `${rect.height + RING_INFLATE_PX * 2}px`;
  cometOverlay().append(el);
  const animation = el.animate(
    [
      { transform: "scale(0.8)", opacity: 1 },
      { transform: "scale(1.45)", opacity: 0 },
    ],
    { duration: 520, easing: "ease-out", fill: "both" },
  );
  await animation.finished.catch(() => undefined);
  el.remove();
}

export function landPulse(target: HTMLElement): void {
  if (typeof target.animate !== "function") return;
  bounce(target);
  void ring(target);
}
