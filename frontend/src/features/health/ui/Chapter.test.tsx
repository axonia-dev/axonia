import { render, screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";

import { Chapter } from "@/features/health/ui/Chapter";

describe("Chapter", () => {
  it("is addressable by the verdict strip's jump link", () => {
    const { container } = render(
      <Chapter
        id="carried"
        title="Who carries it"
        question="how fragile is this?"
      >
        <p>evidence</p>
      </Chapter>,
    );

    expect(container.querySelector("#carried")).toBeInTheDocument();
  });

  it("names itself as a heading", () => {
    render(
      <Chapter
        id="alive"
        title="Is it alive"
        question="is anything actually happening?"
      >
        <p>evidence</p>
      </Chapter>,
    );

    expect(
      screen.getByRole("heading", { name: "Is it alive" }),
    ).toBeInTheDocument();
  });
});
