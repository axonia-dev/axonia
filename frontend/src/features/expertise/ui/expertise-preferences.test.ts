import { afterEach, expect, test } from "vitest";

import {
  parseViewMode,
  storedViewMode,
  storeViewMode,
} from "@/features/expertise/ui/expertise-preferences";

afterEach(() => localStorage.clear());

test("parseViewMode accepts only the two known modes", () => {
  expect(parseViewMode("map")).toBe("map");
  expect(parseViewMode("search")).toBe("search");
  expect(parseViewMode("calendar")).toBeNull();
  expect(parseViewMode(null)).toBeNull();
});

test("a stored mode survives a round trip", () => {
  storeViewMode("map");

  expect(storedViewMode()).toBe("map");
});

test("nothing stored reads as null so the caller can fall back", () => {
  expect(storedViewMode()).toBeNull();
});
