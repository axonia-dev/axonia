import type {
  PairingSummary,
  TopicState,
} from "@/features/mentoring/domain/mentoring";
import {
  MAX_STOPS,
  pathOf,
  type Point,
  standingOfStops,
  stripLayout,
  type TrailLayout,
} from "@/features/mentoring/ui/trail/trail-layout";
import { at } from "@/features/mentoring/ui/trail/TrailStop";
import { Walkers } from "@/features/mentoring/ui/trail/Walkers";
import { cn } from "@/lib/cn";
import { useMediaQuery } from "@/lib/use-media-query";

export function stripLabel(pairing: PairingSummary): string {
  const { stops, nowTopic } = pairing;
  if (stops.length === 0) return "No topics yet";
  const done = stops.filter((state) => state === "DONE").length;
  const now = nowTopic === null ? "" : `, now ${nowTopic}`;
  return `${done} of ${stops.length} topics reached${now}`;
}

/** A phone draws the strip at about half the scale, so its marks grow to stay legible. */
const MARK = "scale-[1.75] sm:scale-100";
const ROAD = "[stroke-width:22] sm:[stroke-width:12]";

function Mark({ state }: Readonly<{ state: TopicState }>) {
  if (state === "DONE") {
    return (
      <>
        <circle r={13} fill="var(--success)" />
        <path
          d="M -3.5 6 V -6.5 L 5 -3.5 L -3.5 -0.5"
          fill="var(--success-foreground)"
          stroke="var(--success-foreground)"
          strokeWidth={1.6}
          strokeLinejoin="round"
        />
      </>
    );
  }
  if (state === "IN_PROGRESS") {
    return (
      <>
        <circle
          r={28}
          fill="color-mix(in srgb, var(--warning) 22%, transparent)"
          className="origin-center [transform-box:fill-box] motion-safe:animate-[trail-halo_2.4s_ease-in-out_infinite]"
        />
        <circle r={17} fill="var(--warning)" />
      </>
    );
  }
  return (
    <circle
      r={11}
      fill="var(--surface)"
      stroke="var(--control-edge)"
      strokeWidth={2.5}
      strokeDasharray="4 4"
    />
  );
}

function Stop({ state, point }: Readonly<{ state: TopicState; point: Point }>) {
  return (
    <g data-state={state} transform={`translate(${point.x} ${point.y})`}>
      <g className={MARK}>
        <Mark state={state} />
      </g>
    </g>
  );
}

function Road({
  layout,
  standing,
  walking,
}: Readonly<{ layout: TrailLayout; standing: number; walking: boolean }>) {
  const walked = layout.legs.slice(0, standing + 1);
  return (
    <>
      <path
        d={layout.path}
        fill="none"
        stroke="var(--border-strong)"
        strokeLinecap="round"
        strokeDasharray={layout.stops.length === 0 ? "1 30" : undefined}
        className={ROAD}
      />
      {walking && (
        <path
          data-testid="strip-walked"
          d={pathOf(layout.start, walked)}
          pathLength={1}
          fill="none"
          stroke="color-mix(in srgb, var(--success) 70%, transparent)"
          strokeLinecap="round"
          className="[stroke-width:22] [stroke-dasharray:1] motion-safe:animate-[trail-draw_0.7s_ease-out_both] sm:[stroke-width:12]"
        />
      )}
    </>
  );
}

/** A pairing's trail as one wide row: the road walked so far, every stop, and the pair standing on it. */
export function StripTrail({ pairing }: Readonly<{ pairing: PairingSummary }>) {
  const still = useMediaQuery("(prefers-reduced-motion: reduce)");
  const stops = pairing.stops.slice(0, MAX_STOPS);
  const layout = stripLayout(stops.length);
  const standing = standingOfStops(stops);
  const walking = (stops[standing] ?? "TODO") !== "TODO";
  const atStart = layout.stops[standing] === undefined;
  const here = layout.stops[standing] ?? layout.start;
  return (
    <div className="flex h-[72px] w-full items-center sm:h-[92px]">
      <div className="relative mx-auto aspect-[1000/130] w-full max-w-[554px] sm:max-w-[708px]">
        <svg
          role="img"
          aria-label={stripLabel(pairing)}
          viewBox={`0 0 ${layout.width} ${layout.height}`}
          className="absolute inset-0 size-full overflow-visible"
        >
          <Road layout={layout} standing={standing} walking={walking} />
          {stops.map((state, index) => (
            <Stop key={index} state={state} point={layout.stops[index]!} />
          ))}
        </svg>
        <span
          aria-hidden
          style={at(here, layout)}
          className={cn(
            "absolute -translate-y-[calc(100%+12px)] scale-75 sm:-translate-y-[calc(100%+16px)] sm:scale-100",
            atStart
              ? "origin-bottom-left -translate-x-2"
              : "origin-bottom -translate-x-1/2",
          )}
        >
          <Walkers pairing={pairing} still={still} />
        </span>
      </div>
    </div>
  );
}
