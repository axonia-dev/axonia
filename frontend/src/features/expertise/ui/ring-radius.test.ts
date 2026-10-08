import { expect, test } from "vitest";

import { markRadius, ringRadius } from "@/features/expertise/ui/ring-radius";

test("no declaration means no ring", () => {
  expect(ringRadius(31, 0)).toBeNull();
});

test("an area with practitioners wears the ring outside its circle", () => {
  expect(ringRadius(31, 2)).toBe(39);
});

test("an area with no practitioners is the ring, drawn at its own radius", () => {
  expect(ringRadius(11, 1, { hasPractitioners: false })).toBe(11);
});

test("an undeclared area's mark is its circle and nothing else", () => {
  expect(markRadius(31, 0)).toBe(31);
});

test("a practised area's mark ends at its ring: its selection is the circle's own stroke", () => {
  expect(markRadius(31, 2)).toBe(39);
});

test("a declaration-only area's mark reserves the room its selection ring needs outside the dashes", () => {
  expect(markRadius(11, 1, { hasPractitioners: false })).toBe(19);
});
