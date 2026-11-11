const MOTE_CLASS =
  "bg-primary pointer-events-none absolute size-[5px] rounded-full shadow-[0_0_8px_var(--primary)]";

function jitter(): number {
  // eslint-disable-next-line sonarjs/pseudo-random -- spreads a decorative flight, guards nothing
  return Math.random();
}

/** A few accent dots drifting up from a point inside `host`, in the community pulse's language.
 *  Each removes itself once its flight ends. */
export function riseMotes(
  host: HTMLElement,
  x: number,
  y: number,
  count: number,
): void {
  for (let index = 0; index < count; index++) {
    const mote = document.createElement("span");
    if (typeof mote.animate !== "function") return;
    mote.setAttribute("aria-hidden", "true");
    mote.className = MOTE_CLASS;
    mote.style.left = `${x}px`;
    mote.style.top = `${y}px`;
    host.append(mote);
    const dx = (jitter() - 0.5) * 30;
    const dy = -(40 + jitter() * 70);
    const scale = 0.6 + jitter() * 0.8;
    const flight = mote.animate(
      [
        { transform: `translate(-50%, -50%) scale(${scale})`, opacity: 0 },
        { opacity: 1, offset: 0.15 },
        {
          transform: `translate(calc(-50% + ${dx}px), calc(-50% + ${dy}px)) scale(${scale * 0.4})`,
          opacity: 0,
        },
      ],
      {
        duration: 1100 + jitter() * 700,
        delay: index * 45,
        easing: "cubic-bezier(.2, .6, .3, 1)",
        fill: "both",
      },
    );
    flight.onfinish = () => {
      mote.remove();
    };
    flight.oncancel = () => {
      mote.remove();
    };
  }
}
