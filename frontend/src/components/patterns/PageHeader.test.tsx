import { render, screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";

import { PageHeader } from "@/components/patterns/PageHeader";

describe("PageHeader", () => {
  it("renders the title as a heading, plus the action", () => {
    // when
    render(
      <PageHeader
        title="Communities"
        action={<button type="button">New</button>}
      />,
    );

    // then
    expect(
      screen.getByRole("heading", { name: "Communities" }),
    ).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "New" })).toBeInTheDocument();
  });

  // jsdom has no layout, so the class contract is the only thing a test can hold: the action
  // stacks under the title on a phone and returns to its side from the first breakpoint up.
  it("stacks the action under the title on a phone, and beside it above that", () => {
    // when
    const { container } = render(
      <PageHeader
        title="Signed-in sessions"
        action={<button type="button">Sign out everywhere</button>}
      />,
    );

    // then
    const header = container.querySelector("header");
    expect(header?.className).toContain("flex-col");
    expect(header?.className).toContain("sm:flex-row");
  });

  it("renders no prose beside the title", () => {
    // when
    const { container } = render(<PageHeader title="Communities" />);

    // then
    expect(container.querySelector("p")).toBeNull();
  });
});
