import { render, screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";

import { PresenterAvatars } from "@/features/events/ui/PresenterAvatars";
import { SpotsPill } from "@/features/events/ui/SpotsPill";

describe("SpotsPill", () => {
  it("states the label it was given", () => {
    render(<SpotsPill label="2 spots open" />);
    expect(screen.getByText("2 spots open")).toBeInTheDocument();
  });
});

describe("PresenterAvatars", () => {
  it("renders one avatar per presenter", () => {
    render(<PresenterAvatars userIds={["u1", "u2"]} users={{}} />);
    expect(screen.getAllByRole("button")).toHaveLength(2);
  });

  it("renders nothing when nobody is presenting", () => {
    const { container } = render(<PresenterAvatars userIds={[]} users={{}} />);
    expect(container).toBeEmptyDOMElement();
  });
});
