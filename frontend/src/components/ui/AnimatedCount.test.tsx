import { render, screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";

import { AnimatedCount } from "@/components/ui/AnimatedCount";

describe("AnimatedCount", () => {
  it("renders the current value", () => {
    // when
    render(<AnimatedCount value={42} />);

    // then
    expect(screen.getByText("42")).toBeInTheDocument();
  });

  it("renders the updated value after a change", () => {
    // given
    const { rerender } = render(<AnimatedCount value={42} />);

    // when
    rerender(<AnimatedCount value={43} />);

    // then
    expect(screen.getByText("43")).toBeInTheDocument();
  });
});
