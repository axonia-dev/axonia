import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";

import { ConfirmDeleteDialog } from "@/components/patterns/ConfirmDeleteDialog";

let user: ReturnType<typeof userEvent.setup>;

beforeEach(() => {
  user = userEvent.setup({ delay: null });
});

const options = [
  { id: 2, label: "Chapter" },
  { id: 3, label: "Tribe" },
];

function renderDialog(
  overrides: Partial<Parameters<typeof ConfirmDeleteDialog>[0]> = {},
) {
  const props = {
    isOpen: true,
    title: "Delete this kind?",
    reassignLabel: "Move communities to…",
    options,
    needsReassign: false,
    reassignTo: null as number | null,
    onReassign: vi.fn(),
    error: null as string | null,
    isDeleting: false,
    onCancel: vi.fn(),
    onConfirm: vi.fn(),
    ...overrides,
  };
  render(<ConfirmDeleteDialog {...props} />);
  return props;
}

describe("ConfirmDeleteDialog", () => {
  it("renders the title and confirms a plain delete", async () => {
    // given
    const { onConfirm } = renderDialog();
    expect(
      screen.getByRole("heading", { name: "Delete this kind?" }),
    ).toBeInTheDocument();
    expect(
      screen.queryByRole("button", { name: /move communities to/i }),
    ).not.toBeInTheDocument();

    // when
    await user.click(screen.getByRole("button", { name: "Delete" }));

    // then
    expect(onConfirm).toHaveBeenCalledOnce();
  });

  it("blocks confirmation until a reassignment target is chosen", async () => {
    // given a record still in use, no target selected yet
    const { onReassign } = renderDialog({ needsReassign: true });
    expect(screen.getByRole("button", { name: "Delete" })).toBeDisabled();

    // when a target is picked
    await user.click(
      screen.getByRole("button", { name: /move communities to/i }),
    );
    await user.click(screen.getByRole("option", { name: "Tribe" }));

    // then the caller is notified of the chosen target
    expect(onReassign).toHaveBeenCalledWith(3);
  });

  it("enables Delete once a target is selected", () => {
    // given needsReassign with a chosen target
    renderDialog({ needsReassign: true, reassignTo: 2 });

    // then
    expect(screen.getByRole("button", { name: "Delete" })).toBeEnabled();
  });

  it("surfaces an error message", () => {
    // given
    renderDialog({ error: "Could not delete this." });

    // then
    expect(screen.getByRole("alert")).toHaveTextContent(
      "Could not delete this.",
    );
  });

  it("labels the confirm button with the given verb instead of the default Delete", async () => {
    // given
    const { onConfirm } = renderDialog({ confirmLabel: "Remove" });
    expect(
      screen.queryByRole("button", { name: "Delete" }),
    ).not.toBeInTheDocument();

    // when
    await user.click(screen.getByRole("button", { name: "Remove" }));

    // then
    expect(onConfirm).toHaveBeenCalledOnce();
  });

  it("cancels", async () => {
    // given
    const { onCancel } = renderDialog();

    // when
    await user.click(screen.getByRole("button", { name: "Cancel" }));

    // then
    expect(onCancel).toHaveBeenCalledOnce();
  });
});
