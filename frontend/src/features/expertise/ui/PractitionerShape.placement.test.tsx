import { act, cleanup, render } from "@testing-library/react";
import { afterEach, describe, expect, it } from "vitest";

import type { ShapeAxis } from "@/features/expertise/domain/practitioner-shape";
import type { MeasureText } from "@/features/expertise/ui/name-style";
import { PractitionerShape } from "@/features/expertise/ui/PractitionerShape";
import {
  layoutShape,
  LINE_HEIGHT,
  MARK_WIDTH,
} from "@/features/expertise/ui/shape-layout";
import { stubTextMeasuring } from "@/test/text-measuring";

const JSDOM_WIDTH = 820;

function axis(label: string, over: Partial<ShapeAxis> = {}): ShapeAxis {
  return {
    key: label,
    kind: "neighbourhood",
    slot: 0,
    label,
    spoken: label.toLowerCase(),
    reach: null,
    declared: false,
    wantsToLearn: false,
    ...over,
  };
}

const MARKED = [
  axis("AROUND THREAT-MODELING", { declared: true, wantsToLearn: true }),
  axis("Observability", { kind: "named", declared: true }),
  axis("AROUND POSTGRES", { reach: "CONTRIBUTOR" }),
  axis("Security", { kind: "named", declared: true, wantsToLearn: true }),
  axis("AROUND REACT", { wantsToLearn: true }),
];

function numberAttr(element: Element, name: string): number {
  return Number(element.getAttribute(name));
}

function translateOf(element: Element): [number, number] {
  const [, x, y] = /translate\((\S+) (\S+)\)/u.exec(
    element.getAttribute("transform") ?? "",
  )!;
  return [Number(x), Number(y)];
}

function markCentres(name: Element): [number, number][] {
  return [...name.parentElement!.querySelectorAll("[data-mark]")].map((m) =>
    m.tagName === "circle"
      ? [numberAttr(m, "cx"), numberAttr(m, "cy")]
      : translateOf(m),
  );
}

function expectDrawnWhereLaidOut(
  container: HTMLElement,
  measure?: MeasureText,
) {
  const layout = layoutShape(
    MARKED.map((a) => ({
      text: a.label,
      kind: a.kind,
      marks: Number(a.declared) + Number(a.wantsToLearn),
      reached: a.reach !== null,
    })),
    JSDOM_WIDTH,
    measure,
  );
  const names = container.querySelectorAll("[data-axis-name]");
  MARKED.forEach((a, i) => {
    const placed = layout.labels.at(i)!;
    const name = names.item(i);
    expect(name.getAttribute("text-anchor")).toBe(placed.anchor);
    for (const tspan of name.querySelectorAll("tspan")) {
      expect(numberAttr(tspan, "x")).toBeCloseTo(placed.textX);
    }
    const marks = markCentres(name);
    expect(marks).toHaveLength(Number(a.declared) + Number(a.wantsToLearn));
    marks.forEach(([x, y], k) => {
      expect(x).toBeCloseTo(placed.marksX + (k + 0.5) * MARK_WIDTH);
      expect(y).toBeCloseTo(
        placed.top + (placed.marksLine + 0.5) * LINE_HEIGHT,
      );
    });
  });
  return layout;
}

describe("PractitionerShape draws where the layout placed each name", () => {
  it("starts each line at the laid-out text x and the marks beside the last line", () => {
    const { container } = render(<PractitionerShape axes={MARKED} />);
    const layout = expectDrawnWhereLaidOut(container);
    expect(layout.labels[0]!.lines).toHaveLength(2);
  });
});

describe("PractitionerShape where the browser measures text", () => {
  let restore = () => {};
  afterEach(() => {
    cleanup();
    restore();
  });

  const perChar: MeasureText = (text) => text.length * 5;

  it("lays the names out by their measured widths from the first frame when the fonts are in", () => {
    const measuring = stubTextMeasuring(5, "loaded");
    restore = measuring.restore;
    const { container } = render(<PractitionerShape axes={MARKED} />);
    expectDrawnWhereLaidOut(container, perChar);
  });

  it("estimates while the fonts load, then lays the names out afresh by their measured widths", () => {
    const measuring = stubTextMeasuring(5);
    restore = measuring.restore;
    const { container } = render(<PractitionerShape axes={MARKED} />);
    expectDrawnWhereLaidOut(container);
    act(() => measuring.loadFonts());
    expectDrawnWhereLaidOut(container, perChar);
  });
});
