import { render, screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";

import { LoveCount } from "@/components/ui/LoveCount";

describe("LoveCount", () => {
  it("shows the count", () => {
    render(<LoveCount count={12} />);
    expect(screen.getByText("12")).toBeInTheDocument();
  });

  it("never shrinks, so it lands in the same place in every row", () => {
    const { container } = render(<LoveCount count={12} />);
    expect(container.firstElementChild).toHaveClass("shrink-0");
  });
});
