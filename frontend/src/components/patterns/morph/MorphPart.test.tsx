import { render, screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";

import { morphName, MorphPart } from "@/components/patterns/morph/MorphPart";

describe("MorphPart", () => {
  it("renders the child as is without an id", () => {
    // when
    render(
      <MorphPart part="badge" id={undefined}>
        <span>badge</span>
      </MorphPart>,
    );

    // then
    expect(screen.getByText("badge")).toBeInTheDocument();
  });

  it("renders the child with an id", () => {
    // when
    render(
      <MorphPart part="badge" id="community-x">
        <span>badge</span>
      </MorphPart>,
    );

    // then
    expect(screen.getByText("badge")).toBeInTheDocument();
  });

  it("names a part by kind and id, as a valid identifier", () => {
    // then
    expect(morphName("title", "session-42")).toBe("morph-title-session-42");
    expect(morphName("name", "community-café club")).toBe(
      "morph-name-community-caf__club",
    );
  });
});
