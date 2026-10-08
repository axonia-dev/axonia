import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it, vi } from "vitest";

import { DisableCommunicationsDialog } from "@/features/admin/ui/DisableCommunicationsDialog";

describe("DisableCommunicationsDialog", () => {
  /**
   * A confirmation restates the consequence, it does not re-explain the setting the switch's
   * own description just gave a moment earlier - so this checks for the decision-relevant
   * facts (both outbound channels, who is affected, the one channel left untouched) rather
   * than a second listing of every message type.
   */
  it("names both outbound channels, the reach, and the in-app exception", () => {
    // when
    render(
      <DisableCommunicationsDialog
        isOpen
        onOpenChange={() => {}}
        onConfirm={() => {}}
      />,
    );

    // then
    const dialog = screen.getByRole("alertdialog");
    expect(dialog).toHaveTextContent(/email/i);
    expect(dialog).toHaveTextContent(/chat/i);
    expect(dialog).toHaveTextContent(/everyone in this organization/i);
    expect(dialog).toHaveTextContent(/in-app notification panel/i);
  });

  /**
   * Pinned well under the switch description's own 55 words, so a regression back toward
   * re-explaining the setting (rather than just restating the consequence of confirming)
   * fails here even though it would still pass the content assertion above.
   */
  it("confirms in far fewer words than the switch description it echoes", () => {
    // when
    render(
      <DisableCommunicationsDialog
        isOpen
        onOpenChange={() => {}}
        onConfirm={() => {}}
      />,
    );

    // then
    const body = screen.getByRole("alertdialog").querySelector("p");
    const wordCount = (body?.textContent ?? "").trim().split(/\s+/).length;
    expect(wordCount).toBeLessThan(40);
  });

  it("puts Cancel first and a single-verb confirm, through DialogFooter", async () => {
    // given
    const onConfirm = vi.fn();
    const user = userEvent.setup({ delay: null });
    render(
      <DisableCommunicationsDialog
        isOpen
        onOpenChange={() => {}}
        onConfirm={onConfirm}
      />,
    );

    // then - Cancel is the first button, the confirm is the second
    const buttons = screen.getAllByRole("button");
    expect(buttons[0]).toHaveTextContent(/cancel/i);
    expect(buttons[1]).toHaveTextContent(/disable/i);

    // when
    await user.click(buttons[1] as HTMLElement);

    // then
    expect(onConfirm).toHaveBeenCalled();
  });

  it("calls onOpenChange(false) from Cancel without confirming", async () => {
    // given
    const onConfirm = vi.fn();
    const onOpenChange = vi.fn();
    const user = userEvent.setup({ delay: null });
    render(
      <DisableCommunicationsDialog
        isOpen
        onOpenChange={onOpenChange}
        onConfirm={onConfirm}
      />,
    );

    // when
    await user.click(screen.getByRole("button", { name: /cancel/i }));

    // then
    expect(onConfirm).not.toHaveBeenCalled();
    expect(onOpenChange).toHaveBeenCalledWith(false);
  });

  it("renders nothing when closed", () => {
    // when
    render(
      <DisableCommunicationsDialog
        isOpen={false}
        onOpenChange={() => {}}
        onConfirm={() => {}}
      />,
    );

    // then
    expect(screen.queryByRole("alertdialog")).not.toBeInTheDocument();
  });
});
