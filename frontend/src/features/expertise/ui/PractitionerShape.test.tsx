import { render, screen, within } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";

import type { ShapeAxis } from "@/features/expertise/domain/practitioner-shape";
import {
  estimateText,
  ringNameStyle,
} from "@/features/expertise/ui/name-style";
import { PractitionerShape } from "@/features/expertise/ui/PractitionerShape";
import { RINGS } from "@/features/expertise/ui/ring-names";
import { TIER_META, TIER_ORDER } from "@/features/expertise/ui/tier-meta";

const placements = vi.hoisted(() => ({ count: 0 }));

vi.mock("@/features/expertise/ui/ring-names", async (importOriginal) => {
  const actual =
    await importOriginal<typeof import("@/features/expertise/ui/ring-names")>();
  return {
    ...actual,
    placeRingNames: (...args: Parameters<typeof actual.placeRingNames>) => {
      placements.count += 1;
      return actual.placeRingNames(...args);
    },
  };
});

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

const AXES = [
  axis("AROUND KAFKA", { reach: "PRACTITIONER" }),
  axis("Observability", {
    kind: "named",
    spoken: "Observability",
    reach: "CONTRIBUTOR",
  }),
  axis("AROUND POSTGRES", { declared: true }),
  axis("Security", { kind: "named", spoken: "Security", wantsToLearn: true }),
  axis("AROUND REACT"),
];

function numberAttr(element: Element, name: string): number {
  return Number(element.getAttribute(name));
}

function polygonPoints(container: HTMLElement): [number, number][] {
  return container
    .querySelector("polygon")!
    .getAttribute("points")!
    .split(" ")
    .map((p) => p.split(",").map(Number) as [number, number]);
}

describe("PractitionerShape", () => {
  afterEach(() => vi.restoreAllMocks());

  it("says in words what each spoke shows", () => {
    render(<PractitionerShape axes={AXES} />);
    const list = screen.getByRole("list");
    expect(
      within(list)
        .getAllByRole("listitem")
        .map((li) => li.textContent),
    ).toEqual([
      "around kafka: Practitioner",
      "Observability: Contributor",
      "around postgres: open to questions",
      "Security: want to learn",
      "around react: none",
    ]);
  });

  it("hides the drawing from assistive technology", () => {
    const { container } = render(<PractitionerShape axes={AXES} />);
    expect(container.querySelector("svg")).toHaveAttribute(
      "aria-hidden",
      "true",
    );
  });

  it("draws one vertex dot per reached axis and no geometry for a mark", () => {
    const { container } = render(<PractitionerShape axes={AXES} />);
    expect(container.querySelectorAll("[data-vertex]")).toHaveLength(2);
    expect(container.querySelectorAll("[data-mark='declared']")).toHaveLength(
      1,
    );
    expect(container.querySelectorAll("[data-mark='learn']")).toHaveLength(1);
  });

  it("pulls the polygon to the centre on a spoke holding only a mark", () => {
    const { container } = render(<PractitionerShape axes={AXES} />);
    const points = polygonPoints(container);
    expect(points).toHaveLength(AXES.length);
    for (const index of [2, 3, 4]) {
      expect(Math.abs(points.at(index)![0])).toBeLessThan(1e-9);
      expect(Math.abs(points.at(index)![1])).toBeLessThan(1e-9);
    }
    expect(Math.hypot(...points[0]!)).toBeGreaterThan(0);
  });

  it("places the ring names once while the drawing stays the same, however often it renders", () => {
    const { rerender } = render(<PractitionerShape axes={AXES} />);
    const placed = placements.count;
    rerender(<PractitionerShape axes={AXES} />);
    rerender(<PractitionerShape axes={AXES} />);
    expect(placements.count).toBe(placed);
    expect(placed).toBeGreaterThan(0);
  });

  it("names every ring", () => {
    const { container } = render(<PractitionerShape axes={AXES} />);
    const ringNames = [...container.querySelectorAll("[data-ring-name]")].map(
      (t) => t.textContent,
    );
    expect(ringNames).toEqual(["Interested", "Contributor", "Practitioner"]);
  });

  it("names all three rings on a phone with sixteen spokes, clear of every dot", () => {
    const axes = Array.from({ length: 16 }, (_, i) =>
      axis(`AROUND A${i}`, { reach: RINGS.at(i % 3) ?? null }),
    );
    vi.spyOn(HTMLElement.prototype, "getBoundingClientRect").mockReturnValue(
      DOMRect.fromRect({ width: 311, height: 400 }),
    );
    const { container } = render(<PractitionerShape axes={axes} />);
    expect(container.querySelector("svg")).toHaveAttribute("width", "311");
    const dots = [...container.querySelectorAll("[data-vertex]")].map(
      (d) => [numberAttr(d, "cx"), numberAttr(d, "cy")] as const,
    );
    const names = [...container.querySelectorAll("[data-ring-name]")];
    expect(names.map((n) => n.textContent)).toEqual([
      "Interested",
      "Contributor",
      "Practitioner",
    ]);
    for (const name of names) {
      const fontSize = numberAttr(name, "font-size");
      expect(fontSize).toBeGreaterThanOrEqual(8);
      const width = estimateText(name.textContent, ringNameStyle(fontSize));
      const x = numberAttr(name, "x");
      const shift = { start: 0, middle: width / 2, end: width }[
        name.getAttribute("text-anchor") as "start" | "middle" | "end"
      ];
      const [left, top] = [x - shift, numberAttr(name, "y") - 0.78 * fontSize];
      for (const [cx, cy] of dots) {
        const nearestX = Math.max(left, Math.min(cx, left + width));
        const nearestY = Math.max(top, Math.min(cy, top + fontSize));
        expect(Math.hypot(cx - nearestX, cy - nearestY)).toBeGreaterThan(6);
      }
    }
  });

  it("sets the names in the map's own display font, an area's in its sans", () => {
    const { container } = render(
      <PractitionerShape
        axes={[...AXES, axis("kafka-streams", { kind: "area" })]}
      />,
    );
    const names = [...container.querySelectorAll("[data-axis-name]")];
    expect(names.map((n) => n.getAttribute("class"))).toEqual([
      "font-display",
      "font-display",
      "font-display",
      "font-display",
      "font-display",
      "font-sans",
    ]);
  });
});

describe("PractitionerShape colours", () => {
  it("paints a vertex in its tier's own colour and the dashed mark in Open to questions'", () => {
    const { container } = render(<PractitionerShape axes={AXES} />);
    const fills = [...container.querySelectorAll("[data-vertex]")].map((d) =>
      d.getAttribute("fill"),
    );
    expect(fills).toEqual([
      TIER_META.PRACTITIONER.colour,
      TIER_META.CONTRIBUTOR.colour,
    ]);
    expect(
      container.querySelector("[data-mark='declared']")!.getAttribute("stroke"),
    ).toBe(TIER_META.OPEN_TO_QUESTIONS.colour);
  });

  it("reads the same colour the lanes' dots and the dashed badge are painted in", () => {
    for (const tier of TIER_ORDER) {
      const { colour, dotClassName } = TIER_META[tier];
      expect(dotClassName).toContain(colour.replaceAll(" ", "_"));
    }
    const dashed = TIER_META.OPEN_TO_QUESTIONS;
    expect(dashed.badgeClassName).toContain(
      `border-[${dashed.colour.replaceAll(" ", "_")}]`,
    );
  });
});
