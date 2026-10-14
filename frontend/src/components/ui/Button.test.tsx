import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it, vi } from "vitest";

import { Button } from "@/components/ui/Button";

describe("Button", () => {
  it("renders its children", () => {
    render(<Button>Click me</Button>);
    expect(
      screen.getByRole("button", { name: "Click me" }),
    ).toBeInTheDocument();
  });

  it("calls onPress when activated", async () => {
    const user = userEvent.setup({ delay: null });
    const onPress = vi.fn();
    render(<Button onPress={onPress}>Go</Button>);
    await user.click(screen.getByRole("button", { name: "Go" }));
    expect(onPress).toHaveBeenCalledTimes(1);
  });

  it("applies the secondary variant classes", () => {
    render(<Button variant="secondary">Sec</Button>);
    expect(screen.getByRole("button", { name: "Sec" }).className).toContain(
      "bg-surface-sunken",
    );
  });

  it("applies the outline variant classes", () => {
    render(<Button variant="outline">Out</Button>);
    expect(screen.getByRole("button", { name: "Out" }).className).toContain(
      "border-border-strong",
    );
  });

  // Outline buttons live on sunken containers - a slot card, an invite row, a secret box. A hover
  // that repainted the fill in the container's own colour would make the button vanish into it,
  // so the hover may firm the border and lift, but never swap the fill.
  it("keeps an outline button's fill on hover, so it cannot dissolve into a sunken container", () => {
    render(<Button variant="outline">Out</Button>);
    const { className } = screen.getByRole("button", { name: "Out" });
    expect(className).toContain("bg-surface");
    expect(className).not.toContain("hover:bg-surface-sunken");
  });

  it("applies the danger variant classes", () => {
    render(<Button variant="danger">Del</Button>);
    expect(screen.getByRole("button", { name: "Del" }).className).toContain(
      "bg-danger",
    );
  });

  it("applies the small size classes", () => {
    render(<Button size="sm">S</Button>);
    expect(screen.getByRole("button", { name: "S" }).className).toContain(
      "text-xs",
    );
  });

  it("merges a custom className", () => {
    render(<Button className="custom-x">X</Button>);
    expect(screen.getByRole("button", { name: "X" }).className).toContain(
      "custom-x",
    );
  });

  it("ignores presses while pending", async () => {
    // given
    const user = userEvent.setup({ delay: null });
    const onPress = vi.fn();
    render(
      <Button isPending onPress={onPress}>
        Save
      </Button>,
    );
    const button = screen.getByRole("button", { name: "Save" });

    // when
    await user.click(button);

    // then
    expect(button).toHaveAttribute("data-pending", "true");
    expect(onPress).not.toHaveBeenCalled();
  });

  it("swaps its label for a full-strength spinner while pending, keeping its name and width", () => {
    // when
    render(<Button isPending>Join</Button>);

    // then
    const button = screen.getByRole("button", { name: "Join" });
    const spinner = button.querySelector("[data-button-spinner]");
    expect(spinner).not.toBeNull();
    expect(screen.getByText("Join")).toHaveClass("opacity-0");
    expect(button.className).not.toContain("data-[pending]:opacity");
  });

  it("shows its label and no spinner when idle", () => {
    // when
    render(<Button>Join</Button>);

    // then
    const button = screen.getByRole("button", { name: "Join" });
    expect(button.querySelector("[data-button-spinner]")).toBeNull();
    expect(screen.getByText("Join")).toBe(button);
  });
});
