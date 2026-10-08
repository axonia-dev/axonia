import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";

import { BudgetBar } from "@/features/events/ui/BudgetBar";

let user: ReturnType<typeof userEvent.setup>;

beforeEach(() => {
  user = userEvent.setup({ delay: null });
});

const LENGTH_FIELD = "Session length in minutes";

describe("BudgetBar", () => {
  it("falls back to a plain total when no budget is set", () => {
    render(<BudgetBar usedMinutes={45} budgetMinutes={null} />);
    expect(screen.getByText(/total 45 min/i)).toBeInTheDocument();
    expect(screen.queryByRole("progressbar")).toBeNull();
  });

  it("renders a progress bar within budget", () => {
    render(<BudgetBar usedMinutes={30} budgetMinutes={60} />);
    const bar = screen.getByRole("progressbar");
    expect(bar).toHaveAttribute("aria-valuenow", "30");
    expect(bar).toHaveAttribute("aria-valuemax", "60");
    expect(screen.getByText(/30 \/ 60 min/i)).toBeInTheDocument();
    expect(screen.queryByText(/over/i)).toBeNull();
  });

  it("flags a soft over-budget warning", () => {
    render(<BudgetBar usedMinutes={75} budgetMinutes={60} />);
    expect(screen.getByText(/15 min over/i)).toBeInTheDocument();
  });
});

describe("an editable budget", () => {
  /** Every other bar in the product reports somebody else's number - a series' length, a
   *  section's time box - and has nothing to write it back to. */
  it("stays a plain number where there is no handler to take a change", () => {
    render(<BudgetBar usedMinutes={30} budgetMinutes={60} />);
    expect(screen.queryByLabelText(LENGTH_FIELD)).toBeNull();
  });

  it("renders the budget as a field when it can be changed", () => {
    render(
      <BudgetBar
        usedMinutes={45}
        budgetMinutes={20}
        onBudgetChange={vi.fn()}
      />,
    );
    expect(screen.getByLabelText(LENGTH_FIELD)).toHaveValue(20);
  });

  it("reports the minutes typed into it", async () => {
    const onBudgetChange = vi.fn();
    render(
      <BudgetBar
        usedMinutes={45}
        budgetMinutes={20}
        onBudgetChange={onBudgetChange}
      />,
    );
    const field = screen.getByLabelText(LENGTH_FIELD);
    await user.clear(field);
    await user.type(field, "90");

    expect(onBudgetChange).toHaveBeenLastCalledWith(90);
  });

  /** A length is optional, and clearing it is how an organizer says this session has no budget to
   *  measure against rather than a budget of nothing. */
  it("reports a cleared field as no budget at all", async () => {
    const onBudgetChange = vi.fn();
    render(
      <BudgetBar
        usedMinutes={45}
        budgetMinutes={20}
        onBudgetChange={onBudgetChange}
      />,
    );
    await user.clear(screen.getByLabelText(LENGTH_FIELD));

    expect(onBudgetChange).toHaveBeenLastCalledWith(null);
  });

  /** The one state the plain total cannot serve: with no length set there is no number on screen
   *  to turn into a field, and a session that never had one is exactly the one wanting a length. */
  it("offers an empty field when no length is set yet", () => {
    render(
      <BudgetBar
        usedMinutes={45}
        budgetMinutes={null}
        onBudgetChange={vi.fn()}
      />,
    );
    expect(screen.getByLabelText(LENGTH_FIELD)).toHaveValue(null);
    expect(screen.queryByRole("progressbar")).toBeNull();
  });
});

describe("over budget", () => {
  /** The fill's own share of the track, which is what a reader compares between two overruns. */
  function fillWidth(container: HTMLElement): string {
    const fill = container.querySelector<HTMLElement>("[data-part='fill']");
    return fill?.style.width ?? "";
  }
  function overrunWidth(container: HTMLElement): string {
    const tail = container.querySelector<HTMLElement>("[data-part='overrun']");
    return tail?.style.width ?? "";
  }

  it("keeps a small overrun visibly small", () => {
    const { container } = render(
      <BudgetBar usedMinutes={21} budgetMinutes={20} />,
    );
    expect(fillWidth(container)).toBe("95.23809523809523%");
    expect(overrunWidth(container)).toBe("4.761904761904762%");
  });

  it("draws a large overrun as most of the track, so the two do not look alike", () => {
    const { container } = render(
      <BudgetBar usedMinutes={60} budgetMinutes={20} />,
    );
    expect(fillWidth(container)).toBe("33.33333333333333%");
    expect(overrunWidth(container)).toBe("66.66666666666666%");
  });

  it("renders no overrun tail while under budget", () => {
    const { container } = render(
      <BudgetBar usedMinutes={10} budgetMinutes={20} />,
    );
    expect(container.querySelector("[data-part='overrun']")).toBeNull();
    expect(fillWidth(container)).toBe("50%");
  });

  it("still states the overrun in words, so colour is never the only carrier", () => {
    render(<BudgetBar usedMinutes={60} budgetMinutes={20} />);
    expect(screen.getByText(/40 min over/)).toBeInTheDocument();
  });

  it("reports the real total to assistive technology rather than the clamped one", () => {
    render(<BudgetBar usedMinutes={60} budgetMinutes={20} />);
    expect(screen.getByRole("progressbar")).toHaveAttribute(
      "aria-valuetext",
      "60 of 20 minutes used",
    );
  });
});
