import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";

import { StatusChip } from "@/components/ui/StatusChip";

let user: ReturnType<typeof userEvent.setup>;

beforeEach(() => {
  user = userEvent.setup({ delay: null });
});

describe("StatusChip", () => {
  it("renders its label", () => {
    render(<StatusChip tone="success" label="Verified" />);

    expect(screen.getByText("Verified")).toBeInTheDocument();
  });

  it("tints itself with the success token for a success tone", () => {
    render(<StatusChip tone="success" label="Verified" />);

    expect(screen.getByText("Verified")).toHaveClass("text-success");
  });

  it("tints itself with the danger token for a danger tone", () => {
    render(<StatusChip tone="danger" label="Unhealthy" />);

    expect(screen.getByText("Unhealthy")).toHaveClass("text-danger");
  });

  it("tints itself with the warning token for a warning tone", () => {
    render(<StatusChip tone="warning" label="Not verified" />);

    expect(screen.getByText("Not verified")).toHaveClass("text-warning");
  });

  it("falls back to muted foreground for a neutral tone", () => {
    render(<StatusChip tone="neutral" label="Disabled" />);

    expect(screen.getByText("Disabled")).toHaveClass("text-muted-foreground");
  });

  it("forwards an aria-describedby so a caller can attach detail text", () => {
    render(
      <StatusChip
        tone="danger"
        label="Unhealthy"
        aria-describedby="detail-7"
      />,
    );

    expect(screen.getByText("Unhealthy")).toHaveAttribute(
      "aria-describedby",
      "detail-7",
    );
  });

  it("keeps the dot out of the accessibility tree", () => {
    const { container } = render(
      <StatusChip tone="success" label="Verified" />,
    );

    expect(container.querySelector('[aria-hidden="true"]')).toBeInTheDocument();
  });

  it("renders a provided icon in place of the dot", () => {
    const { container } = render(
      <StatusChip
        tone="neutral"
        label="Synced directory"
        icon={<svg data-testid="chip-icon" />}
      />,
    );

    expect(screen.getByTestId("chip-icon")).toBeInTheDocument();
    expect(container.querySelector(".size-1\\.5")).not.toBeInTheDocument();
  });

  it("stays a plain span with no onPress, so a state nobody can act on is not announced as a button", () => {
    render(<StatusChip tone="success" label="Verified" />);

    expect(screen.queryByRole("button", { name: "Verified" })).toBeNull();
  });

  it("becomes a button that calls onPress, keeping the same tint", async () => {
    const onPress = vi.fn();
    render(
      <StatusChip tone="success" label="Wrap-up sent" onPress={onPress} />,
    );

    const chip = screen.getByRole("button", { name: "Wrap-up sent" });
    await user.click(chip);

    expect(onPress).toHaveBeenCalledOnce();
    expect(chip).toHaveClass("text-success");
  });

  it("maintains h-6 and flex-none for centre-line alignment with icon button", () => {
    render(<StatusChip tone="success" label="Verified" />);

    const chip = screen.getByText("Verified");
    expect(chip).toHaveClass("h-6");
    expect(chip).toHaveClass("flex-none");
  });
});
