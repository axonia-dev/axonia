import { render, screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";

import { TagChips } from "@/components/ui/TagChips";

describe("TagChips", () => {
  it("renders nothing for an empty list", () => {
    const { container } = render(<TagChips tags={[]} />);
    expect(container).toBeEmptyDOMElement();
  });

  it("renders a bordered surface chip per tag", () => {
    render(<TagChips tags={["kafka"]} />);
    const chip = screen.getByText("#kafka");
    expect(chip.className).toContain("bg-surface");
    expect(chip.className).toContain("border-border");
  });

  // Some browsers drop list semantics from a <ul> that is not block-level, and inline flow is
  // exactly what lets the chips wrap around SlotMeta's floated control.
  it("keeps its list semantics in inline flow", () => {
    render(<TagChips tags={["kafka", "streaming"]} />);
    expect(screen.getByRole("list")).toHaveClass("inline");
    expect(screen.getAllByRole("listitem")).toHaveLength(2);
  });
});
