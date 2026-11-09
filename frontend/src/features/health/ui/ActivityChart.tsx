import { useState } from "react";

import type { HealthSnapshot } from "@/features/health/domain/health";
import type { Span } from "@/features/health/domain/window";
import {
  bucketSpans,
  dayMonth,
  dayRange,
  windowDaysOf,
  windowSpan,
} from "@/features/health/domain/window";
import { cn } from "@/lib/cn";
import { plural } from "@/lib/plural";

import { monotonePath } from "./monotone-path";

type Series = HealthSnapshot["series"];

const BUCKETS = 12;
const WIDTH = 1200;
const STEP = WIDTH / BUCKETS;
const HEIGHT = 190;
const TOP = 20;
const BASELINE = 170;
const GRIDLINES = [20, 80, 140];
const AXIS_TICKS = [0, 4, 8];
const PLOT = BASELINE - TOP;
const HEADROOM = 1.15;
const JOINS_SHARE = 0.25;

interface Bucket {
  span: Span;
  talks: number;
  people: number;
  joins: number;
}

function bucketsOf(series: Series, span: Span): Bucket[] {
  return bucketSpans(span, BUCKETS).map((bucketSpan, index) => ({
    span: bucketSpan,
    talks: series.talks[index] ?? 0,
    people: series.people[index] ?? 0,
    joins: series.joins[index] ?? 0,
  }));
}

function countsOf(bucket: Bucket): string[] {
  return [
    plural(bucket.talks, "talk"),
    plural(bucket.people, "person"),
    plural(bucket.joins, "join"),
  ];
}

function summaryOf(buckets: Bucket[], windowDays: number): string {
  const sum = (pick: (bucket: Bucket) => number) =>
    buckets.reduce((total, bucket) => total + pick(bucket), 0);
  const peak = Math.max(0, ...buckets.map((bucket) => bucket.people));
  return `Last ${windowDays} days: ${plural(
    sum((b) => b.talks),
    "talk",
  )}, ${plural(
    sum((b) => b.joins),
    "join",
  )}, at most ${plural(peak, "person")} involved in one stretch`;
}

function scale(values: number[], height: number): (value: number) => number {
  const top = Math.max(1, ...values);
  return (value) => BASELINE - (value / top) * height;
}

function Marks({ buckets }: Readonly<{ buckets: Bucket[] }>) {
  const y = scale(
    buckets.flatMap((bucket) => [
      bucket.talks * HEADROOM,
      bucket.people * HEADROOM,
    ]),
    PLOT,
  );
  const joinsY = scale(
    buckets.map((bucket) => bucket.joins),
    PLOT * JOINS_SHARE,
  );
  const line = monotonePath(
    buckets.map((bucket, index) => ({
      x: (index + 0.5) * STEP,
      y: y(bucket.people),
    })),
  );
  const area = `${line} L${(WIDTH - STEP / 2).toFixed(1)},${BASELINE} L${(STEP / 2).toFixed(1)},${BASELINE} Z`;

  return (
    <svg
      aria-hidden
      viewBox={`0 0 ${WIDTH} ${HEIGHT}`}
      preserveAspectRatio="none"
      className="absolute inset-0 size-full"
    >
      {GRIDLINES.map((gridY) => (
        <line
          key={gridY}
          x1="0"
          y1={gridY}
          x2={WIDTH}
          y2={gridY}
          className="stroke-border"
          vectorEffect="non-scaling-stroke"
        />
      ))}
      <path d={area} data-mark="area" className="fill-data-area" />
      <path
        d={line}
        data-mark="people"
        fill="none"
        strokeWidth="2"
        strokeLinejoin="round"
        className="stroke-data-2"
        vectorEffect="non-scaling-stroke"
      />
      {buckets.map((bucket, index) => (
        <rect
          key={`talks-${index}`}
          data-mark="talks"
          x={index * STEP + 26}
          y={y(bucket.talks)}
          width="34"
          height={BASELINE - y(bucket.talks)}
          rx="5"
          className="fill-data-1"
        />
      ))}
      {buckets.map((bucket, index) => (
        <rect
          key={`joins-${index}`}
          data-mark="joins"
          x={index * STEP + 64}
          y={joinsY(bucket.joins)}
          width="20"
          height={BASELINE - joinsY(bucket.joins)}
          rx="4"
          className="fill-data-3"
        />
      ))}
      <line
        x1="0"
        y1={BASELINE}
        x2={WIDTH}
        y2={BASELINE}
        className="stroke-border-strong"
        vectorEffect="non-scaling-stroke"
      />
    </svg>
  );
}

function tipPlacement(index: number): { left: string; className: string } {
  if (index < BUCKETS / 2) {
    return { left: `${((index + 1) / BUCKETS) * 100}%`, className: "ml-1" };
  }
  return {
    left: `${(index / BUCKETS) * 100}%`,
    className: "-ml-1 -translate-x-full",
  };
}

function Tip({ bucket, index }: Readonly<{ bucket: Bucket; index: number }>) {
  const [talks, people, joins] = countsOf(bucket);
  const placement = tipPlacement(index);
  return (
    <div
      aria-hidden
      data-tip
      style={{ left: placement.left }}
      className={cn(
        "bg-surface-sunken border-border-strong text-foreground pointer-events-none absolute top-3 z-10 rounded-[10px] border px-2.5 py-2 text-xs leading-normal whitespace-nowrap shadow-md",
        placement.className,
      )}
    >
      <p className="text-muted-foreground">{dayRange(bucket.span)}</p>
      <p className="font-semibold">{talks}</p>
      <p>{people}</p>
      <p>{joins}</p>
    </div>
  );
}

function Legend() {
  const entries = [
    { label: "talks given", swatch: "size-[9px] rounded-[3px] bg-data-1" },
    { label: "people involved", swatch: "h-0.5 w-3 rounded-full bg-data-2" },
    { label: "joins", swatch: "size-[9px] rounded-[3px] bg-data-3" },
  ];
  return (
    <ul className="text-muted-foreground flex flex-wrap gap-x-3.5 gap-y-1 text-xs">
      {entries.map(({ label, swatch }) => (
        <li key={label} className="flex items-center gap-1.5">
          <span aria-hidden className={swatch} />
          {label}
        </li>
      ))}
    </ul>
  );
}

/**
 * Talks as bars over people involved as a curve and its soft area, joins as small bars on their own
 * scale, over the snapshot's twelve buckets. Drawn in the community's data tones only.
 */
export function ActivityChart({
  series,
  windowId,
  generatedAt,
}: Readonly<{ series: Series; windowId: string; generatedAt: string }>) {
  const [active, setActive] = useState<number | null>(null);
  const span = windowSpan(windowId, generatedAt);
  const buckets = bucketsOf(series, span);
  const activeBucket = active === null ? undefined : buckets[active];
  const ticks = [
    ...AXIS_TICKS.map((index) => buckets[index]?.span.startMs ?? span.startMs),
    span.endMs,
  ];

  return (
    <section className="border-border bg-surface rounded-lg border px-[22px] py-5">
      <header className="flex flex-wrap items-center justify-between gap-x-4 gap-y-2">
        <h2 className="text-muted-foreground text-[11px] font-semibold tracking-[0.08em] uppercase">
          Activity
        </h2>
        <Legend />
      </header>
      <div
        role="group"
        aria-label={summaryOf(buckets, windowDaysOf(windowId))}
        className="relative mt-3.5 h-[190px]"
      >
        <Marks buckets={buckets} />
        <ol className="absolute inset-0 grid grid-cols-12">
          {buckets.map((bucket, index) => (
            <li key={bucket.span.startMs} className="flex">
              <button
                type="button"
                aria-label={`${dayRange(bucket.span)}: ${countsOf(bucket).join(", ")}`}
                onFocus={() => setActive(index)}
                onBlur={() => setActive(null)}
                onMouseEnter={() => setActive(index)}
                onMouseLeave={() => setActive(null)}
                className={cn(
                  "focus-visible:ring-focus-ring flex-1 rounded-md outline-none focus-visible:ring-2",
                  active === index && "bg-foreground/5",
                )}
              />
            </li>
          ))}
        </ol>
        {activeBucket && active !== null && (
          <Tip bucket={activeBucket} index={active} />
        )}
      </div>
      <div
        aria-hidden
        className="text-muted-foreground mt-1.5 flex justify-between text-[11px]"
      >
        {ticks.map((ms) => (
          <span key={ms}>{dayMonth(ms)}</span>
        ))}
      </div>
    </section>
  );
}
