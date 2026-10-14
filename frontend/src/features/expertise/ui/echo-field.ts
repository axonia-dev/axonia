import { easeOutCubic } from "@/components/patterns/ceremony/timeline";
import { type Echo, progressOf } from "@/features/expertise/ui/echo-timeline";

export interface EchoField {
  /** How far the echo has carried each item, 0 untouched and 1 settled, as of the last `at`. */
  amounts: Float64Array;
  at: (elapsedMs: number) => void;
}

/**
 * `amountAt` for every item of one layer at once, and fast enough to run every frame on a ground of tens of
 * thousands of vertices. Where an item stands never changes during an echo, so its distance to every drop is worked
 * out once; a frame then only moves each wave's front and compares. Nothing is allocated per frame.
 */
export function echoField(echo: Echo, xy: Float32Array): EchoField {
  const items = xy.length / 2;
  const { waves } = echo;
  const distances = new Float64Array(items * waves.length);
  waves.forEach((wave, w) => {
    for (let item = 0; item < items; item++) {
      const dx = xy[item * 2]! - wave.x;
      const dy = xy[item * 2 + 1]! - wave.y;
      distances[w * items + item] = Math.sqrt(dx * dx + dy * dy);
    }
  });
  const amounts = new Float64Array(items);

  const carry = (w: number, elapsedMs: number) => {
    const wave = waves[w]!;
    const progress = progressOf(wave, elapsedMs);
    if (progress === 0) return;
    const front = easeOutCubic(progress) * wave.reach;
    const done = progress === 1;
    for (let item = 0; item < items; item++) {
      const distance = distances[w * items + item]!;
      // A finished wave has taken all of its reach, stated exactly, as `amountAt` does.
      const k =
        done && distance <= wave.reach
          ? 1
          : Math.min(1, Math.max(0, (front - distance) / wave.feather));
      if (k > amounts[item]!) amounts[item] = k;
    }
  };

  return {
    amounts,
    at: (elapsedMs) => {
      amounts.fill(0);
      for (let w = 0; w < waves.length; w++) carry(w, elapsedMs);
    },
  };
}
