import { useEffect, useRef } from "react";

import { useMediaQuery } from "@/lib/use-media-query";

export const CONFETTI_PIECES = 22;

const COLOURS = [
  "var(--success)",
  "var(--primary)",
  "var(--secondary)",
  "var(--warning)",
  "var(--success-ink)",
];

const GOLDEN_ANGLE = 2.39996;

function flight(index: number): Keyframe[] {
  const angle = index * GOLDEN_ANGLE;
  const distance = 60 + ((index * 37) % 90);
  const x = Math.cos(angle) * distance;
  const y = Math.sin(angle) * distance + 50;
  const spin = 180 + ((index * 83) % 360);
  return [
    { transform: "translate(0, 0) rotate(0deg)", opacity: 1 },
    {
      transform: `translate(${x}px, ${y}px) rotate(${spin}deg)`,
      opacity: 0,
    },
  ];
}

/** A short burst from the middle of its positioned parent; nothing at all under reduced motion. */
export function Confetti({ onDone }: Readonly<{ onDone: () => void }>) {
  const reduced = useMediaQuery("(prefers-reduced-motion: reduce)");
  const burst = useRef<HTMLSpanElement>(null);

  useEffect(() => {
    const pieces = [...(burst.current?.children ?? [])] as HTMLElement[];
    const flights = pieces.map((piece, index) =>
      piece.animate(flight(index), {
        duration: 900 + ((index * 53) % 400),
        easing: "cubic-bezier(.2,.7,.4,1)",
        fill: "forwards",
      }),
    );
    let live = true;
    const settle = () => {
      if (live) onDone();
    };
    Promise.all(flights.map((one) => one.finished)).then(settle, settle);
    return () => {
      live = false;
      flights.forEach((one) => one.cancel());
    };
  }, [onDone]);

  if (reduced) return null;
  return (
    <span
      ref={burst}
      aria-hidden
      className="pointer-events-none absolute top-1/2 left-1/2 z-20"
    >
      {Array.from({ length: CONFETTI_PIECES }, (_, index) => (
        <span
          key={index}
          className="absolute h-3 w-2 rounded-[2px]"
          style={{ background: COLOURS[index % COLOURS.length] }}
        />
      ))}
    </span>
  );
}
