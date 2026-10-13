import {
  type Cue,
  type CueStyle,
  fadeIn,
  part,
  sheen,
} from "@/components/patterns/ceremony/cues";
import {
  canvasColour,
  drawSparks,
  type Painter,
  viewportField,
} from "@/components/patterns/ceremony/paint";
import {
  loadTextFont,
  sampleStep,
  textTargets,
} from "@/components/patterns/ceremony/sample-text";
import {
  createSeeds,
  fitTargets,
  type Point,
  sparkBudget,
  sparksAt,
  type SparkSeed,
} from "@/components/patterns/ceremony/spark-field";
import type { Beat } from "@/components/patterns/ceremony/timeline";

export const BIRTH_LENGTH = 3.4;

const BIRTH_BEATS = {
  sparks: { start: 0.15, duration: 1.8 },
  sparksFade: { start: 2.25, duration: 0.6 },
  surface: { start: 2.0, duration: 0.9 },
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
  readonly colour: string;
}

/** Spark radius per sample step: the letters' dots touch without running together. */
const SIZE_PER_STEP = 0.16;

function prepare(name: HTMLElement, card: HTMLElement, hue: string): Prepared {
  const field = viewportField();
  const budget = sparkBudget(
    field.width,
    field.height,
    window.devicePixelRatio || 1,
  );
  const { targets, step } = fitTargets(
    (at) => textTargets(name, card, at),
    sampleStep(name, card),
    budget,
  );
  return {
    targets,
    seeds: createSeeds(targets.length, 7, step * SIZE_PER_STEP),
    colour: canvasColour(hue, card),
  };
}

/** Sparks that fly in from beyond the edges and condense into the name's letterforms. */
export function birthPainter(hue: string): Painter {
  let prepared: Prepared | null = null;
  let loading = false;
  return (context, t, card) => {
    const name = card.querySelector<HTMLElement>(part("name"));
    if (name === null) {
      return;
    }
    if (prepared === null) {
      if (!loading) {
        loading = true;
        void loadTextFont(name)
          .catch(() => undefined)
          .then(() => {
            prepared = prepare(name, card, hue);
          });
      }
      return;
    }
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
      prepared.colour,
    );
  };
}
