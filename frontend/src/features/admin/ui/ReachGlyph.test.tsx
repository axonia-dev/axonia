import { render } from "@testing-library/react";
import { describe, expect, it } from "vitest";

import { ReachGlyph } from "@/features/admin/ui/ReachGlyph";

describe("ReachGlyph", () => {
  it("draws one bar per level", () => {
    const { container } = render(
      <ReachGlyph levels={["none", "some", "all"]} />,
    );
    expect(container.querySelectorAll("span > span")).toHaveLength(3);
  });

  it("gives each level its own literal height class", () => {
    const { container } = render(
      <ReachGlyph levels={["none", "some", "all"]} />,
    );
    const bars = [...container.querySelectorAll("span > span")];
    expect(bars[0]?.className).toContain("h-[3px]");
    expect(bars[1]?.className).toContain("h-[9px]");
    expect(bars[2]?.className).toContain("h-4");
  });

  it("draws neutral ink, so a rail of roles has no accent but the one that is selected", () => {
    const { container } = render(<ReachGlyph levels={["some", "all"]} />);
    const bars = [...container.querySelectorAll("span > span")];
    expect(bars[0]?.className).toContain("bg-muted-foreground");
    expect(bars[1]?.className).toContain("bg-muted-foreground");
    expect(bars.map((b) => b.className).join(" ")).not.toContain("bg-primary");
  });

  it("takes the brand colour on the selected row, which is the one thing on the rail that carries it", () => {
    const { container } = render(
      <ReachGlyph levels={["some", "all"]} selected />,
    );
    const bars = [...container.querySelectorAll("span > span")];
    expect(bars[0]?.className).toContain("bg-primary/60");
    expect(bars[1]?.className).toContain("bg-primary");
  });

  it("is hidden from assistive technology, which reads the row's own name instead", () => {
    const { container } = render(<ReachGlyph levels={["all"]} />);
    expect(container.firstElementChild).toHaveAttribute("aria-hidden", "true");
  });
});
