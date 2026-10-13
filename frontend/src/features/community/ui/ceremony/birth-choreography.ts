import {
  type Cue,
  type CueStyle,
  fadeIn,
  part,
  sheen,
} from "@/components/patterns/ceremony/cues";
import {
  drawSparks,
  type Painter,
  viewportField,
} from "@/components/patterns/ceremony/paint";
import { textTargets } from "@/components/patterns/ceremony/sample-text";
import {
  createSeeds,
  type Point,
  sparkBudget,
  sparksAt,
  type SparkSeed,
} from "@/components/patterns/ceremony/spark-field";
import type { Beat } from "@/components/patterns/ceremony/timeline";

export const BIRTH_LENGTH = 3.4;

export const BIRTH_BEATS = {
  sparks: { start: 0.15, duration: 1.8 },
  sparksFade: { start: 2.25, duration: 0.6 },
  surface: { start: 1.9, duration: 0.7 },
  name: { start: 2.1, duration: 0.5 },
  glow: { start: 2.6, duration: 0.8 },
  sheen: { start: 2.45, duration: 0.75 },
  meta: { start: 2.9, duration: 0.5 },
} satisfies Record<string, Beat>;

function glow(hue: string): (p: number) => CueStyle {
  return (p) => ({
    "text-shadow": p >= 1 ? "" : `0 0 ${Math.round(24 * (1 - p))}px ${hue}`,
  });
}

export function birthCues(hue: string): Cue[] {
  return [
    { selector: part("surface"), beat: BIRTH_BEATS.surface, style: fadeIn },
    { selector: part("name"), beat: BIRTH_BEATS.name, style: fadeIn },
    { selector: part("name"), beat: BIRTH_BEATS.glow, style: glow(hue) },
    { selector: part("name"), beat: BIRTH_BEATS.sheen, style: sheen },
    ...["meta", "description", "footer"].map((name) => ({
      selector: part(name),
      beat: BIRTH_BEATS.meta,
      style: fadeIn,
    })),
  ];
}

interface Prepared {
  readonly targets: readonly Point[];
  readonly seeds: readonly SparkSeed[];
}

function prepare(name: HTMLElement, card: HTMLElement): Prepared {
  const targets = textTargets(name, card);
  const field = viewportField();
  const budget = sparkBudget(
    field.width,
    field.height,
    window.devicePixelRatio || 1,
  );
  return { targets, seeds: createSeeds(Math.min(budget, targets.length), 7) };
}

/** Sparks that fly in from beyond the edges and condense into the name's letterforms. */
export function birthPainter(hue: string): Painter {
  let prepared: Prepared | null = null;
  return (context, t, card) => {
    const name = card.querySelector<HTMLElement>(part("name"));
    if (name === null) {
      return;
    }
    prepared ??= prepare(name, card);
    const frame = card.getBoundingClientRect();
    const targets = prepared.targets.map((point) => ({
      x: frame.left + point.x,
      y: frame.top + point.y,
    }));
    drawSparks(
      context,
      sparksAt(
        {
          kind: "condense",
          targets,
          beat: BIRTH_BEATS.sparks,
          fadeOut: BIRTH_BEATS.sparksFade,
        },
        prepared.seeds,
        t,
        viewportField(),
      ),
      hue,
    );
  };
}
