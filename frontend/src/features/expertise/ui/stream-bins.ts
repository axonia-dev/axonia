import type { PlacedContribution } from "@/features/expertise/ui/contribution-placement";

interface StreamBand {
  tag: string;
  slot: number;
  values: number[];
}

export interface StreamLayout {
  bins: number;
  from: number;
  to: number;
  bands: StreamBand[];
}

/**
 * How wide the smoothing kernel is, as a share of the whole history. Fixed in bins it was a different amount of
 * smoothing on every screen, and at the width the map is now drawn at it was almost none: a community that
 * contributes in bursts came out as a row of separate spikes with bare floor between them, which reads as a chart
 * of nothing rather than as a history. A share of the span means the same shape whatever the box.
 */
const SMOOTH_SHARE = 0.08;
const SIGMA_SHARE = SMOOTH_SHARE / 2.5;

function smooth(values: number[]): number[] {
  const radius = Math.max(1, Math.round(values.length * SMOOTH_SHARE));
  const twoSigmaSquared = 2 * (values.length * SIGMA_SHARE) ** 2;

  return values.map((_, i) => {
    let total = 0;
    let weight = 0;
    for (let d = -radius; d <= radius; d++) {
      const j = i + d;
      if (j < 0 || j >= values.length) continue;
      const w = Math.exp(-(d * d) / twoSigmaSquared);
      total += values[j]! * w;
      weight += w;
    }
    return weight === 0 ? 0 : total / weight;
  });
}

/**
 * One band per topic rather than one per neighbourhood, which is what turns the stream from a handful of shapes
 * into a picture of a practice. Three neighbourhoods give three bands, every one of them peaking on the same dates
 * because they are the dates the community was active, so the drawing is three copies of one silhouette. A band per
 * topic puts each thing the community works on in its own layer, and the neighbourhood survives as the colour the
 * layer is painted in, which is the same trade the terrain makes.
 */
export function buildStream(
  all: PlacedContribution[],
  bins: number,
): StreamLayout {
  if (!Number.isInteger(bins) || bins < 1) {
    throw new RangeError(`A stream needs at least one bin, not ${bins}.`);
  }

  // occurredAt is parsed from an unvalidated string upstream, so one unreadable date arrives as NaN. Left in, it
  // poisons Math.min and every band collapses; the honest reading is the history of the contributions that do have
  // a date.
  const marks = all.filter((m) => Number.isFinite(m.occurredAt));
  if (marks.length === 0) return { bins, from: 0, to: 0, bands: [] };

  const times = marks.map((m) => m.occurredAt);
  const from = Math.min(...times);
  const to = Math.max(...times);
  // A community whose whole history landed on one day has no span to divide by; one bin holds everything.
  const span = to - from || 1;

  const byTag = new Map<string, StreamBand>();
  for (const mark of marks) {
    const band = byTag.get(mark.tag) ?? {
      tag: mark.tag,
      slot: mark.slot,
      values: new Array<number>(bins).fill(0),
    };
    const bin = Math.min(
      bins - 1,
      Math.floor(((mark.occurredAt - from) / span) * bins),
    );
    band.values[bin] = band.values[bin]! + 1;
    byTag.set(mark.tag, band);
  }

  return { bins, from, to, bands: ordered([...byTag.values()]) };
}

/**
 * Neighbours stay together so their shared colour reads as one region, and within a neighbourhood the busiest topic
 * takes the middle of the stack, where a band is thickest and its name fits. The tag breaks the tie, so the same
 * history draws the same picture on every load.
 */
function ordered(bands: StreamBand[]): StreamBand[] {
  const total = (band: StreamBand) =>
    band.values.reduce((sum, value) => sum + value, 0);

  const sorted = [...bands];
  sorted.sort(
    (a, b) =>
      a.slot - b.slot || total(b) - total(a) || a.tag.localeCompare(b.tag),
  );
  return sorted.map((band) => ({ ...band, values: smooth(band.values) }));
}
