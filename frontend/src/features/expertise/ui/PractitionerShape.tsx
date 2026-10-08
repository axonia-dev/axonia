import { useMemo, useRef } from "react";

import type { ShapeAxis } from "@/features/expertise/domain/practitioner-shape";
import {
  type MeasureText,
  nameStyle,
  ringNameStyle,
  textProps,
} from "@/features/expertise/ui/name-style";
import {
  fractionOf,
  placeRingNames,
  type RingName,
  RINGS,
} from "@/features/expertise/ui/ring-names";
import {
  axisAngle,
  layoutShape,
  LINE_HEIGHT,
  MARK_WIDTH,
  type PlacedLabel,
  polar,
  type ShapeLayout,
  viewBoxOf,
} from "@/features/expertise/ui/shape-layout";
import { TIER_META } from "@/features/expertise/ui/tier-meta";
import { useGraphSize } from "@/features/expertise/ui/use-graph-size";
import { useNameMeasure } from "@/features/expertise/ui/use-name-measure";

const BASELINE_LIFT = 3;

type Point = [number, number];

function spokenLine(axis: ShapeAxis): string {
  const parts = [
    ...(axis.reach === null ? [] : [TIER_META[axis.reach].label]),
    ...(axis.declared ? ["open to questions"] : []),
    ...(axis.wantsToLearn ? ["want to learn"] : []),
  ];
  return `${axis.spoken}: ${parts.length === 0 ? "none" : parts.join(", ")}`;
}

interface GridProps {
  count: number;
  radius: number;
  ringNames: readonly RingName[];
}

function RingNames({ names }: Readonly<{ names: readonly RingName[] }>) {
  return names.map(({ reach, x, y, anchor, fontSize }) => (
    <text
      key={reach}
      data-ring-name
      x={x}
      y={y}
      textAnchor={anchor}
      {...textProps(ringNameStyle(fontSize))}
      fill="var(--muted-foreground)"
      stroke="var(--surface)"
      strokeWidth={3}
      paintOrder="stroke"
    >
      {TIER_META[reach].label}
    </text>
  ));
}

function Grid({ count, radius, ringNames }: Readonly<GridProps>) {
  return (
    <g>
      <g stroke="var(--border)" fill="none">
        {RINGS.map((ring) => (
          <circle key={ring} r={radius * fractionOf(ring)} />
        ))}
        {Array.from({ length: count }, (_, i) => {
          const [x, y] = polar(axisAngle(i, count), radius);
          return <line key={i} x1={0} y1={0} x2={x} y2={y} />;
        })}
      </g>
      <RingNames names={ringNames} />
    </g>
  );
}

function Polygon({
  axes,
  vertices,
}: Readonly<{ axes: ShapeAxis[]; vertices: readonly Point[] }>) {
  return (
    <g>
      <polygon
        points={vertices.map((v) => v.join(",")).join(" ")}
        fill="color-mix(in srgb, var(--foreground) 9%, transparent)"
        stroke="var(--foreground)"
        strokeWidth={1.5}
        strokeLinejoin="round"
      />
      {axes.map((a, i) => {
        const vertex = vertices.at(i);
        if (a.reach === null || vertex === undefined) return null;
        return (
          <circle
            key={a.key}
            data-vertex
            cx={vertex[0]}
            cy={vertex[1]}
            r={4.5}
            fill={TIER_META[a.reach].colour}
            stroke="var(--surface)"
            strokeWidth={1.5}
          />
        );
      })}
    </g>
  );
}

function Marks({
  axis,
  label,
}: Readonly<{ axis: ShapeAxis; label: PlacedLabel }>) {
  const kinds = [
    ...(axis.declared ? ["declared"] : []),
    ...(axis.wantsToLearn ? ["learn"] : []),
  ];
  const y = label.top + (label.marksLine + 0.5) * LINE_HEIGHT;
  return kinds.map((kind, i) => {
    const x = label.marksX + (i + 0.5) * MARK_WIDTH;
    return kind === "declared" ? (
      <circle
        key={kind}
        data-mark="declared"
        cx={x}
        cy={y}
        r={4.5}
        fill="none"
        stroke={TIER_META.OPEN_TO_QUESTIONS.colour}
        strokeWidth={1.4}
        strokeDasharray="2 1.6"
      />
    ) : (
      <path
        key={kind}
        data-mark="learn"
        d="M-4 2.5 L0 -3.5 L4 2.5"
        transform={`translate(${x} ${y})`}
        fill="none"
        stroke="var(--foreground)"
        strokeWidth={1.5}
        strokeLinejoin="round"
        strokeLinecap="round"
      />
    );
  });
}

function AxisName({
  axis,
  label,
  width,
}: Readonly<{ axis: ShapeAxis; label: PlacedLabel; width: number }>) {
  const quiet = axis.reach === null && !axis.declared && !axis.wantsToLearn;
  return (
    <g>
      <text
        data-axis-name
        {...textProps(nameStyle(axis.kind, width))}
        textAnchor={label.anchor}
        fill={quiet ? "var(--muted-foreground)" : "var(--foreground)"}
      >
        {label.lines.map((line, i) => (
          <tspan
            key={`${i}-${line}`}
            x={label.textX}
            y={label.top + (i + 1) * LINE_HEIGHT - BASELINE_LIFT}
          >
            {line}
          </tspan>
        ))}
      </text>
      <Marks axis={axis} label={label} />
    </g>
  );
}

interface DrawingProps {
  axes: ShapeAxis[];
  layout: ShapeLayout;
  measure: MeasureText | undefined;
}

function Drawing({ axes, layout, measure }: Readonly<DrawingProps>) {
  const { radius, width, height, labels } = layout;
  const viewBox = useMemo(() => viewBoxOf(layout), [layout]);
  const { vertices, ringNames } = useMemo(() => {
    const outline = axes.map((a, i) =>
      polar(axisAngle(i, axes.length), radius * fractionOf(a.reach)),
    );
    const dots = outline.filter((_, i) => axes.at(i)?.reach != null);
    return {
      vertices: outline,
      ringNames: placeRingNames(
        axes.length,
        radius,
        { dots, outline, names: labels, viewBox },
        measure,
      ),
    };
  }, [axes, radius, labels, viewBox, measure]);
  return (
    <svg
      aria-hidden="true"
      viewBox={`${viewBox.left} ${viewBox.top} ${width} ${height}`}
      width={width}
      height={height}
      className="block"
    >
      <Grid count={axes.length} radius={radius} ringNames={ringNames} />
      <Polygon axes={axes} vertices={vertices} />
      {axes.map((axis, i) => {
        const label = labels.at(i);
        return label?.visible === true ? (
          <AxisName key={axis.key} axis={axis} label={label} width={width} />
        ) : null;
      })}
    </svg>
  );
}

/** A member's practice as one shape: tiers reach, marks sit beside names, and the words live in a hidden list. */
export function PractitionerShape({ axes }: Readonly<{ axes: ShapeAxis[] }>) {
  const ref = useRef<HTMLDivElement>(null);
  const size = useGraphSize(ref);
  const width = size?.width;
  const measure = useNameMeasure();
  const layout = useMemo(
    () =>
      width === undefined
        ? null
        : layoutShape(
            axes.map((a) => ({
              text: a.label,
              kind: a.kind,
              marks: Number(a.declared) + Number(a.wantsToLearn),
              reached: a.reach !== null,
            })),
            width,
            measure,
          ),
    [axes, width, measure],
  );
  return (
    <div ref={ref} className="w-full">
      {layout !== null && (
        <Drawing axes={axes} layout={layout} measure={measure} />
      )}
      <ul className="sr-only">
        {axes.map((a) => (
          <li key={a.key}>{spokenLine(a)}</li>
        ))}
      </ul>
    </div>
  );
}
