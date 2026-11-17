import { render, screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";

import { AreaTag } from "@/components/ui/AreaTag";
import { areaHue } from "@/lib/area-hue";

describe("AreaTag", () => {
  it("marks a shared area with ✦ and says so to a screen reader", () => {
    // when
    render(<AreaTag area="kafka" shared />);

    // then
    const tag = screen.getByRole("img", { name: "kafka, shared" });
    expect(tag).toHaveTextContent("kafka✦");
  });

  it("names an area that is not shared by its text alone", () => {
    // when
    render(<AreaTag area="kafka" />);

    // then
    expect(screen.queryByRole("img")).not.toBeInTheDocument();
    expect(screen.getByText("kafka")).toBeInTheDocument();
  });

  it("wears the tint of its area in both themes", () => {
    // given
    const { light, dark } = areaHue("kafka");

    // when
    render(<AreaTag area="kafka" />);

    // then
    const tag = screen.getByText("kafka").parentElement!;
    expect(tag.style.getPropertyValue("--area-bg")).toBe(light.background);
    expect(tag.style.getPropertyValue("--area-fg-dark")).toBe(dark.foreground);
  });
});
