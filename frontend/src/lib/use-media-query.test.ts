import { renderHook } from "@testing-library/react";
import { afterEach, describe, expect, it } from "vitest";

import { useMediaQuery } from "@/lib/use-media-query";

const original = window.matchMedia;

function stubMatchMedia(matches: boolean) {
  window.matchMedia = (query: string): MediaQueryList => ({
    matches,
    media: query,
    onchange: null,
    addListener: () => {},
    removeListener: () => {},
    addEventListener: () => {},
    removeEventListener: () => {},
    dispatchEvent: () => false,
  });
}

afterEach(() => {
  window.matchMedia = original;
});

describe("useMediaQuery", () => {
  it("returns false when the query does not match", () => {
    stubMatchMedia(false);
    const { result } = renderHook(() => useMediaQuery("(min-width: 768px)"));
    expect(result.current).toBe(false);
  });

  it("returns true when the query matches", () => {
    stubMatchMedia(true);
    const { result } = renderHook(() => useMediaQuery("(min-width: 768px)"));
    expect(result.current).toBe(true);
  });
});
