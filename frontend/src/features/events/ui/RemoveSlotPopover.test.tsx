import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";

import { RemoveSlotPopover } from "@/features/events/ui/RemoveSlotPopover";

let user: ReturnType<typeof userEvent.setup>;

beforeEach(() => {
  user = userEvent.setup({ delay: null });
});

describe("RemoveSlotPopover", () => {
  it("renders nothing while closed", () => {
    const { container } = render(
      <RemoveSlotPopover
        slotLabel="Hexagons"
        isOpen={false}
        isPending={false}
        onConfirm={() => {}}
        onOpenChange={() => {}}
      />,
    );
    expect(container).toBeEmptyDOMElement();
  });

  it("names the slot it will remove", () => {
    render(
      <RemoveSlotPopover
        slotLabel="Hexagons"
        isOpen
        isPending={false}
        onConfirm={() => {}}
        onOpenChange={() => {}}
      />,
    );
    expect(screen.getByRole("dialog")).toHaveTextContent(
      /remove\s+hexagons\s+from the schedule\?/i,
    );
  });

  it("confirms and closes when Remove is pressed", async () => {
    const onConfirm = vi.fn();
    const onOpenChange = vi.fn();
    render(
      <RemoveSlotPopover
        slotLabel="Hexagons"
        isOpen
        isPending={false}
        onConfirm={onConfirm}
        onOpenChange={onOpenChange}
      />,
    );
    await user.click(screen.getByRole("button", { name: /^remove$/i }));
    expect(onConfirm).toHaveBeenCalledOnce();
    expect(onOpenChange).toHaveBeenCalledWith(false);
  });

  it("closes without confirming when Cancel is pressed", async () => {
    const onConfirm = vi.fn();
    const onOpenChange = vi.fn();
    render(
      <RemoveSlotPopover
        slotLabel="Hexagons"
        isOpen
        isPending={false}
        onConfirm={onConfirm}
        onOpenChange={onOpenChange}
      />,
    );
    await user.click(screen.getByRole("button", { name: /cancel/i }));
    expect(onConfirm).not.toHaveBeenCalled();
    expect(onOpenChange).toHaveBeenCalledWith(false);
  });

  it("disables the Remove button while a removal is pending", () => {
    render(
      <RemoveSlotPopover
        slotLabel="Hexagons"
        isOpen
        isPending
        onConfirm={() => {}}
        onOpenChange={() => {}}
      />,
    );
    expect(screen.getByRole("button", { name: /^remove$/i })).toBeDisabled();
  });
});
