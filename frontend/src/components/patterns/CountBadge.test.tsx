import { render, screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";

import { CountBadge } from "@/components/patterns/CountBadge";

describe("CountBadge", () => {
  it("renders the value", () => {
    // given / when
    render(<CountBadge value={7} />);

    // then
    expect(screen.getByText("7")).toBeInTheDocument();
  });

  it("applies a custom className to the pill", () => {
    // given / when
    render(<CountBadge value={1} className="ml-auto" />);

    // then
    expect(screen.getByText("1").closest("span.ml-auto")).not.toBeNull();
  });

  it("carries no unit for a screen reader by default", () => {
    // given / when
    render(<CountBadge value={7} />);

    // then
    expect(screen.queryByText("placements")).toBeNull();
  });

  it("names the unit for a screen reader when given a label", () => {
    // given / when
    render(<CountBadge value={7} label="placements" />);

    // then
    expect(screen.getByText("placements")).toHaveClass("sr-only");
  });
});
