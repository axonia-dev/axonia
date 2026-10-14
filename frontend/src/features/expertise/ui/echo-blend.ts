import type { MapHighlight } from "@/features/expertise/ui/map-highlight";

export interface Tinted {
  /** x, y per item, in the layer's own layout coordinates. */
  xy: Float32Array;
  /** Values per item: 3 for an RGB attribute, 4 for RGBA, 1 for an opacity. */
  stride: number;
  /** What is drawn now. A blend writes here and `commit` pushes it to the GPU objects. */
  values: Float32Array;
  targetFor: (highlight: MapHighlight) => Float32Array;
  commit: () => void;
}

export interface Blend {
  apply: (amount: (x: number, y: number) => number) => void;
}

/**
 * A transition from what is on screen now, never from a picture the layer remembers: a new term arriving mid-echo
 * carries on from where the last one got to instead of flashing back to full strength.
 */
export function blendOf(tinted: Tinted, to: Float32Array): Blend {
  const from = tinted.values.slice();
  const { xy, stride, values } = tinted;
  return {
    apply: (amount) => {
      for (let item = 0; item < xy.length / 2; item++) {
        const k = amount(xy[item * 2]!, xy[item * 2 + 1]!);
        for (let c = 0; c < stride; c++) {
          const at = item * stride + c;
          values[at] = from[at]! + (to[at]! - from[at]!) * k;
        }
      }
      tinted.commit();
    },
  };
}

export function paintNow(tinted: Tinted, highlight: MapHighlight): void {
  tinted.values.set(tinted.targetFor(highlight));
  tinted.commit();
}
