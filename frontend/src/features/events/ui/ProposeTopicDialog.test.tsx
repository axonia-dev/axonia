import { fireEvent, render, screen } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";

import { ProposeTopicDialog } from "@/features/events/ui/ProposeTopicDialog";

vi.mock("@/features/events/ui/ProposeTopicForm", () => ({
  ProposeTopicForm: ({ onProposed }: { onProposed?: () => void }) => (
    <button type="button" onClick={() => onProposed?.()}>
      submit
    </button>
  ),
}));

describe("ProposeTopicDialog", () => {
  it("opens the dialog from the add-new button and closes on propose", () => {
    render(<ProposeTopicDialog slug="backend-guild" />);

    expect(screen.queryByRole("dialog")).not.toBeInTheDocument();

    fireEvent.click(screen.getByRole("button", { name: /add new/i }));
    expect(screen.getByRole("dialog")).toBeInTheDocument();

    fireEvent.click(screen.getByRole("button", { name: "submit" }));
    expect(screen.queryByRole("dialog")).not.toBeInTheDocument();
  });
});
