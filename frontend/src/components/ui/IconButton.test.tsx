import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { Trash2 } from "lucide-react";
import { beforeEach, describe, expect, it, vi } from "vitest";

import { IconButton } from "@/components/ui/IconButton";

let user: ReturnType<typeof userEvent.setup>;

beforeEach(() => {
  user = userEvent.setup({ delay: null });
});

describe("IconButton", () => {
  it("names itself from its label", () => {
    render(
      <IconButton label="Delete Acme">
        <Trash2 />
      </IconButton>,
    );

    expect(
      screen.getByRole("button", { name: "Delete Acme" }),
    ).toBeInTheDocument();
  });

  it("calls onPress when clicked", async () => {
    const onPress = vi.fn();
    render(
      <IconButton label="Delete Acme" onPress={onPress}>
        <Trash2 />
      </IconButton>,
    );

    await user.click(screen.getByRole("button", { name: "Delete Acme" }));

    expect(onPress).toHaveBeenCalledOnce();
  });

  it("rests muted rather than in the brand colour", () => {
    render(
      <IconButton label="Delete Acme">
        <Trash2 />
      </IconButton>,
    );

    const button = screen.getByRole("button", { name: "Delete Acme" });
    expect(button).toHaveClass("text-muted-foreground");
    expect(button).not.toHaveClass("text-primary");
  });

  it("keeps a square box so a row of buttons shares one centre line", () => {
    render(
      <IconButton label="Delete Acme">
        <Trash2 />
      </IconButton>,
    );

    expect(screen.getByRole("button", { name: "Delete Acme" })).toHaveClass(
      "size-8",
    );
  });

  it("reads as unavailable when disabled instead of looking pressable", () => {
    render(
      <IconButton label="Delete Acme" isDisabled>
        <Trash2 />
      </IconButton>,
    );

    const button = screen.getByRole("button", { name: "Delete Acme" });
    expect(button).toBeDisabled();
    expect(button.className).toContain("disabled:opacity-50");
    expect(button.className).toContain("disabled:pointer-events-none");
  });

  it("swaps its icon for a full-strength spinner while pending, keeping its name", () => {
    render(
      <IconButton label="Delete Acme" isPending>
        <Trash2 data-testid="icon" />
      </IconButton>,
    );

    const button = screen.getByRole("button", { name: "Delete Acme" });
    expect(button).toHaveAttribute("data-pending", "true");
    expect(button.querySelector("[data-button-spinner]")).not.toBeNull();
    expect(screen.queryByTestId("icon")).not.toBeInTheDocument();
    expect(button.className).not.toContain("data-[pending]:opacity-50");
  });

  it("answers hover in the danger token for a danger tone", () => {
    render(
      <IconButton label="Delete Acme" tone="danger">
        <Trash2 />
      </IconButton>,
    );

    expect(
      screen.getByRole("button", { name: "Delete Acme" }).className,
    ).toContain("hover:text-danger");
  });
});
