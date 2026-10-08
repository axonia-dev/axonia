import { render, screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";

import type { PlacedLabel } from "@/features/expertise/ui/map-labels-placement";
import { MapLabelLayer } from "@/features/expertise/ui/MapLabelLayer";

function placed(
  text: string,
  kind: PlacedLabel["label"]["kind"],
  tags: string[],
): PlacedLabel {
  return {
    label: { key: text, text, kind, weight: 1, at: [] },
    seat: { x: 0, y: 0, tags },
    x: 120,
    y: 80,
  };
}

const LABELS = [
  placed("AROUND KAFKA", "neighbourhood", ["kafka"]),
  placed("flink", "area", ["flink"]),
];

function names() {
  return screen
    .getAllByTestId("map-label")
    .map((node) => [node.textContent, node.dataset["lit"]]);
}

describe("MapLabelLayer", () => {
  it("writes each name where the drawing put it", () => {
    render(<MapLabelLayer labels={LABELS} highlight={null} />);

    expect(screen.getByText("AROUND KAFKA")).toHaveStyle({
      left: "120px",
      top: "80px",
    });
  });

  /**
   * The panel beside the map lists every area in full and the overlay's buttons already carry the name of every
   * mark, so these words would only put a second, shorter copy of the map in the reading order.
   */
  it("stays out of the reading order", () => {
    const { container } = render(
      <MapLabelLayer labels={LABELS} highlight={null} />,
    );

    expect(container.querySelector("[aria-hidden='true']")).toContainElement(
      screen.getByText("flink"),
    );
  });

  it("leaves every name at full strength when the panel is about the whole map", () => {
    render(<MapLabelLayer labels={LABELS} highlight={null} />);

    expect(names()).toStrictEqual([
      ["AROUND KAFKA", "true"],
      ["flink", "true"],
    ]);
  });

  // A name is lit by the ground it is standing on, not by the neighbourhood it belongs to: a headline left white
  // over a region a reading has set aside points the reader at the one region the reading is not about.
  it("sets back a name standing on ground the reading is not about", () => {
    render(<MapLabelLayer labels={LABELS} highlight={new Set(["kafka"])} />);

    expect(names()).toStrictEqual([
      ["AROUND KAFKA", "true"],
      ["flink", "false"],
    ]);
    expect(screen.getByText("flink").className).toContain("opacity-35");
  });

  it("writes nothing when nothing found room", () => {
    render(<MapLabelLayer labels={[]} highlight={null} />);

    expect(screen.queryByTestId("map-label")).toBeNull();
  });

  // A name a leader chose and a name detection guessed must not read as the same claim.
  it("sets a named area apart from a detected neighbourhood, and keeps the leader's casing", () => {
    render(
      <MapLabelLayer
        labels={[...LABELS, placed("Data in Motion", "named", ["kafka"])]}
        highlight={null}
      />,
    );

    const named = screen.getByText("Data in Motion");
    const around = screen.getByText("AROUND KAFKA");
    expect(named).toHaveAttribute("data-kind", "named");
    expect(around).toHaveAttribute("data-kind", "neighbourhood");
    expect(screen.getByText("flink")).toHaveAttribute("data-kind", "area");
    expect(named.className).not.toContain("uppercase");
  });

  /**
   * Casing cannot be the difference: a leader may type a name in capitals, and "DDD & CQRS" then read as one more
   * detected headline. The shape is, and jsdom paints nothing, so the classes that draw it are all there is to read.
   */
  it("stands a named area on a ruled plate that a neighbourhood never has, whatever its casing", () => {
    render(
      <MapLabelLayer
        labels={[...LABELS, placed("DDD & CQRS", "named", ["kafka"])]}
        highlight={null}
      />,
    );

    const plate = ["rounded-full", "border", "bg-[var(--map-halo)]"];
    expect(screen.getByText("DDD & CQRS")).toHaveClass(...plate);
    for (const cls of plate) {
      expect(screen.getByText("AROUND KAFKA")).not.toHaveClass(cls);
    }
  });
});
