import { render, screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";

import {
  CarryChip,
  CheckpointStatusChip,
  DueChip,
} from "@/features/events/ui/ActionItemChips";

describe("DueChip", () => {
  it("says nothing about an item nobody put a date on", () => {
    const { container } = render(
      <DueChip dueDate={null} now={new Date("2026-09-30T01:00:00Z")} />,
    );

    expect(container).toBeEmptyDOMElement();
  });

  /**
   * A due date is a civil date, so `new Date("2026-09-30")` is UTC midnight. Compared against a
   * local `now` it reads as passed from mid-afternoon the day before for every viewer west of
   * UTC - the chip tinting itself from the afternoon of the 29th.
   */
  it("does not tint an item as passed on the evening before it is due", () => {
    const { container } = render(
      <DueChip dueDate="2026-09-30" now={new Date("2026-09-30T01:00:00Z")} />,
    );

    expect(screen.getByText("Due Sep 30")).toBeInTheDocument();
    expect(container.firstElementChild?.className).not.toContain(
      "text-warning",
    );
  });

  /** The checkpoint is the accountability mechanism; the due date is a target that may fall well
   *  before it. "Overdue" in danger red made the softer of the two signals the loudest thing in
   *  the row, and read as a verdict on an item that is still going to be discussed. */
  it("never calls a passed due date overdue", () => {
    render(
      <DueChip dueDate="2020-09-11" now={new Date("2026-09-20T00:00:00Z")} />,
    );

    expect(screen.getByText("Due Sep 11")).toBeInTheDocument();
    expect(screen.queryByText(/Overdue/i)).not.toBeInTheDocument();
  });

  it("words a future due date the same way", () => {
    render(
      <DueChip dueDate="2099-12-31" now={new Date("2026-09-20T00:00:00Z")} />,
    );

    expect(screen.getByText("Due Dec 31")).toBeInTheDocument();
  });

  it("tints a passed due date without shouting it", () => {
    const { container } = render(
      <DueChip dueDate="2020-09-11" now={new Date("2026-09-20T00:00:00Z")} />,
    );

    expect(container.firstElementChild?.className).toContain("text-warning");
    expect(container.firstElementChild?.className).not.toContain("text-danger");
  });
});

describe("CheckpointStatusChip", () => {
  it("states that the answering session is still unresolved", () => {
    render(<CheckpointStatusChip unresolved unreported={false} />);

    expect(screen.getByText("Checkpoint pending")).toBeInTheDocument();
  });

  /** An unresolved checkpoint cannot also be unreported: there is no session to have missed. */
  it("stays on the unresolved wording when both arms are set", () => {
    render(<CheckpointStatusChip unresolved unreported />);

    expect(screen.getByText("Checkpoint pending")).toBeInTheDocument();
    expect(
      screen.queryByText("Checkpoint not reported"),
    ).not.toBeInTheDocument();
  });

  it("flags a session that came and went without the item being answered", () => {
    render(<CheckpointStatusChip unresolved={false} unreported />);

    expect(screen.getByText("Checkpoint not reported")).toBeInTheDocument();
  });

  it("says nothing about an item whose checkpoint is still ahead", () => {
    const { container } = render(
      <CheckpointStatusChip unresolved={false} unreported={false} />,
    );

    expect(container).toBeEmptyDOMElement();
  });
});

// The row and the tab render the same carry count, so they must word it the same way.
describe("CarryChip", () => {
  it("words a single carry the way the row does", () => {
    render(<CarryChip carryCount={1} />);

    expect(screen.getByText("Carried once")).toBeInTheDocument();
  });

  it("words a repeated carry the way the row does", () => {
    render(<CarryChip carryCount={3} />);

    expect(screen.getByText("Carried 3 times")).toBeInTheDocument();
  });

  it("says nothing at all about an item that was never carried", () => {
    const { container } = render(<CarryChip carryCount={0} />);

    expect(container).toBeEmptyDOMElement();
  });
});
