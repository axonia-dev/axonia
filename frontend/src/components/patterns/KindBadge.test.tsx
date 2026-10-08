import { render, screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";

import { KindBadge } from "@/components/patterns/KindBadge";

describe("KindBadge", () => {
  it("renders the kind label", () => {
    // when
    render(<KindBadge kindId={1} label="Guild" />);

    // then
    expect(screen.getByText("Guild")).toBeInTheDocument();
  });

  it("assigns a stable colour from the kind id", () => {
    // when
    render(<KindBadge kindId={1} label="Guild" />);

    // then
    expect(screen.getByText("Guild").style.getPropertyValue("--k")).toMatch(
      /^#[0-9a-f]{6}$/i,
    );
  });
});
