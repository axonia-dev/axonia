import { render } from "@testing-library/react";
import { describe, expect, it } from "vitest";

import { AccentIcon } from "@/components/ui/AccentIcon";

describe("AccentIcon", () => {
  it("renders a tinted tile carrying the accent custom property", () => {
    const { container } = render(
      <AccentIcon name="library" accent="var(--primary)" className="size-10" />,
    );
    const tile = container.firstElementChild as HTMLElement;
    expect(tile).toBeTruthy();
    expect(tile.style.getPropertyValue("--k")).toBe("var(--primary)");
    expect(tile.className).toContain("size-10");
  });
});
