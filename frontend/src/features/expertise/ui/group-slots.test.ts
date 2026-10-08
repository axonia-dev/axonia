import { expect, test } from "vitest";

import { withSlots } from "@/features/expertise/ui/group-slots";

const slotsOf = (groups: { slot: number }[]) => groups.map((g) => g.slot);

test("with nothing named every neighbourhood keeps its own place as its slot", () => {
  const baseline = [{ tags: ["a", "b"] }, { tags: ["c"] }, { tags: ["d"] }];

  const { detected } = withSlots(baseline, baseline, []);

  expect(slotsOf(detected)).toStrictEqual([0, 1, 2]);
});

test("a neighbourhood keeps the slot of the baseline one it shares the most tags with", () => {
  const baseline = [{ tags: ["a", "b", "c"] }, { tags: ["d", "e"] }];

  const { detected } = withSlots(baseline, [{ tags: ["e", "d"] }], []);

  expect(slotsOf(detected)).toStrictEqual([1]);
});

test("two neighbourhoods split from one: the larger share keeps the slot, the other takes the lowest free one", () => {
  const baseline = [{ tags: ["a", "b", "c", "d", "e"] }, { tags: ["f"] }];

  const { detected } = withSlots(
    baseline,
    [{ tags: ["a", "b"] }, { tags: ["c", "d", "e"] }, { tags: ["f"] }],
    [],
  );

  expect(slotsOf(detected)).toStrictEqual([2, 0, 1]);
});

test("a named area drawn evenly from two free slots takes the lower one", () => {
  const baseline = [{ tags: ["a", "b"] }, { tags: ["c", "d"] }];

  const { named } = withSlots(baseline, [], [{ tags: ["a", "c"] }]);

  expect(slotsOf(named)).toStrictEqual([0]);
});

test("a neighbourhood wins a tied slot over a named area, which takes the lowest free one", () => {
  const baseline = [{ tags: ["a", "b", "c", "d"] }, { tags: ["e"] }];

  const slotted = withSlots(
    baseline,
    [{ tags: ["a", "b"] }, { tags: ["e"] }],
    [{ tags: ["c", "d"] }, { tags: ["z"] }],
  );

  expect(slotsOf(slotted.detected)).toStrictEqual([0, 1]);
  expect(slotsOf(slotted.named)).toStrictEqual([2, 3]);
});

const EIGHT = Array.from({ length: 8 }, (_, i) => ({ tags: [`t${i}`] }));
const drawnFrom = (slots: number[]) =>
  slots.map((slot) => ({ tags: [`t${slot}`] }));

test("a surviving slot past the palette that would share a colour takes the lowest free slot of an unused one", () => {
  const { detected } = withSlots(EIGHT, drawnFrom([0, 2, 6]), []);

  expect(slotsOf(detected)).toStrictEqual([0, 2, 1]);
});

test("moving a clashing slot never takes the colour of a higher slot that had its own", () => {
  const { detected } = withSlots(EIGHT, drawnFrom([0, 2, 6, 7]), []);

  expect(slotsOf(detected)).toStrictEqual([0, 2, 3, 7]);
});

test("a surviving slot past the palette keeps its place when its colour is still its own", () => {
  const { detected } = withSlots(EIGHT, drawnFrom([0, 2, 7]), []);

  expect(slotsOf(detected)).toStrictEqual([0, 2, 7]);
});

test("a map with more groups than colours keeps every slot, since some colour has to repeat", () => {
  const { detected } = withSlots(EIGHT, EIGHT, []);

  expect(slotsOf(detected)).toStrictEqual([0, 1, 2, 3, 4, 5, 6, 7]);
});
