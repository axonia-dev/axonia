import { act, cleanup, renderHook } from "@testing-library/react";
import { afterEach, describe, expect, it } from "vitest";

import { nameStyle } from "@/features/expertise/ui/name-style";
import { useNameMeasure } from "@/features/expertise/ui/use-name-measure";
import { stubTextMeasuring } from "@/test/text-measuring";

const NEIGHBOURHOOD = nameStyle("neighbourhood", 704);
const AREA = nameStyle("area", 704);

function probes() {
  return document.body.querySelectorAll("svg[aria-hidden] text");
}

describe("useNameMeasure", () => {
  let restore = () => {};
  afterEach(() => {
    cleanup();
    restore();
  });

  it("measures nothing and adds nothing to the page where no text is laid out", () => {
    const { result } = renderHook(() => useNameMeasure());
    expect(result.current).toBeUndefined();
    expect(document.body.querySelectorAll("svg")).toHaveLength(0);
  });

  it("measures from the first render when the fonts are already in, each text once in the style it is drawn in", () => {
    const measuring = stubTextMeasuring(5, "loaded");
    restore = measuring.restore;
    const { result } = renderHook(() => useNameMeasure());
    const measure = result.current!;
    expect(probes()).toHaveLength(0);
    expect(measure("AROUND", NEIGHBOURHOOD)).toBe(30);
    expect(measure("kafka-streams", AREA)).toBe(65);
    const neighbourhood = [...probes()].find(
      (t) => t.getAttribute("letter-spacing") === "0.1em",
    );
    expect(neighbourhood).toHaveAttribute("class", "font-display");
    expect(neighbourhood).toHaveAttribute("font-size", "11");
    expect(probes()).toHaveLength(2);
    const before = measuring.calls();
    measure("AROUND", NEIGHBOURHOOD);
    expect(measuring.calls()).toBe(before);
  });

  it("waits while the fonts load, and measures afresh each time another face comes in", () => {
    const measuring = stubTextMeasuring(5);
    restore = measuring.restore;
    const { result } = renderHook(() => useNameMeasure());
    expect(result.current).toBeUndefined();
    act(() => measuring.loadFonts());
    const first = result.current;
    expect(first).toBeDefined();
    act(() => measuring.loadFonts());
    expect(result.current).toBeDefined();
    expect(result.current).not.toBe(first);
  });

  it("keeps one probe per style however often the fonts come in", () => {
    const measuring = stubTextMeasuring(5, "loaded");
    restore = measuring.restore;
    const { result } = renderHook(() => useNameMeasure());
    result.current!("AROUND", NEIGHBOURHOOD);
    act(() => measuring.loadFonts());
    result.current!("AROUND", NEIGHBOURHOOD);
    act(() => measuring.loadFonts());
    result.current!("AROUND", NEIGHBOURHOOD);
    expect(probes()).toHaveLength(1);
  });

  it("stops listening for fonts and takes its probes away once the drawing has gone", () => {
    const measuring = stubTextMeasuring(5, "loaded");
    restore = measuring.restore;
    const { result, unmount } = renderHook(() => useNameMeasure());
    result.current!("AROUND", NEIGHBOURHOOD);
    expect(measuring.listeners()).toBe(1);
    unmount();
    expect(measuring.listeners()).toBe(0);
    expect(document.body.querySelectorAll("svg")).toHaveLength(0);
  });
});
