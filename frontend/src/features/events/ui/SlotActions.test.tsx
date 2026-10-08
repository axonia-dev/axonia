import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";

import { SlotActions } from "@/features/events/ui/SlotActions";

const baseProps = {
  removePending: false,
  onEdit: vi.fn(),
  onRemove: vi.fn(),
};

let user: ReturnType<typeof userEvent.setup>;

beforeEach(() => {
  user = userEvent.setup({ delay: null });
  vi.clearAllMocks();
});

describe("SlotActions - management gating", () => {
  it("shows Edit and Remove to a presenter or a schedule manager", () => {
    render(<SlotActions {...baseProps} canManageSlot />);
    expect(screen.getByLabelText("Edit slot")).toBeInTheDocument();
    expect(screen.getByLabelText("Remove slot")).toBeInTheDocument();
  });

  it("hides management actions from a plain member", () => {
    render(<SlotActions {...baseProps} canManageSlot={false} />);
    expect(screen.queryByLabelText("Edit slot")).not.toBeInTheDocument();
    expect(screen.queryByLabelText("Remove slot")).not.toBeInTheDocument();
  });
});

describe("SlotActions - interactions", () => {
  it("calls onEdit when the edit button is pressed", async () => {
    const onEdit = vi.fn();
    render(<SlotActions {...baseProps} canManageSlot onEdit={onEdit} />);
    await user.click(screen.getByLabelText("Edit slot"));
    expect(onEdit).toHaveBeenCalledOnce();
  });

  it("calls onRemove when the remove button is pressed", async () => {
    const onRemove = vi.fn();
    render(<SlotActions {...baseProps} canManageSlot onRemove={onRemove} />);
    await user.click(screen.getByLabelText("Remove slot"));
    expect(onRemove).toHaveBeenCalledOnce();
  });

  it("disables the remove button while a removal is pending", () => {
    render(<SlotActions {...baseProps} canManageSlot removePending />);
    expect(screen.getByLabelText("Remove slot")).toBeDisabled();
  });
});
