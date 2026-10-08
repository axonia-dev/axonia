import { render, screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";

import { EmptyState } from "@/components/patterns/EmptyState";

describe("EmptyState", () => {
  it("renders the title, description, and action slot", () => {
    // when
    render(
      <EmptyState
        title="No communities yet"
        description="Start the first one."
        action={<button type="button">Start</button>}
      />,
    );

    // then
    expect(screen.getByText("No communities yet")).toBeInTheDocument();
    expect(screen.getByText("Start the first one.")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Start" })).toBeInTheDocument();
  });

  it("renders an icon above the title when provided", () => {
    render(<EmptyState title="Nothing here" icon={<svg data-testid="i" />} />);
    expect(screen.getByTestId("i")).toBeInTheDocument();
  });
});
