import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

import { MonthNav } from "@/features/events/ui/MonthNav";

let user: ReturnType<typeof userEvent.setup>;

beforeEach(() => {
  user = userEvent.setup({ delay: null });
  vi.useFakeTimers({ toFake: ["Date"] });
  vi.setSystemTime(new Date("2026-06-14T12:00:00Z"));
});
afterEach(() => vi.useRealTimers());

function renderNav(monthIndex0: number, onMonthChange = vi.fn()) {
  render(
    <MonthNav
      month={{ year: 2026, monthIndex0 }}
      activeMonths={[]}
      onMonthChange={onMonthChange}
    />,
  );
  return onMonthChange;
}

describe("MonthNav", () => {
  it("steps back a month", async () => {
    const onMonthChange = renderNav(5);
    await user.click(screen.getByRole("button", { name: "Previous month" }));
    expect(onMonthChange).toHaveBeenCalledWith({ year: 2026, monthIndex0: 4 });
  });

  it("steps forward a month", async () => {
    const onMonthChange = renderNav(5);
    await user.click(screen.getByRole("button", { name: "Next month" }));
    expect(onMonthChange).toHaveBeenCalledWith({ year: 2026, monthIndex0: 6 });
  });

  // A sticky month needs a way out of it, in one interaction rather than two.
  it("returns to the current month", async () => {
    const onMonthChange = renderNav(2);
    await user.click(screen.getByRole("button", { name: "Today" }));
    expect(onMonthChange).toHaveBeenCalledWith({ year: 2026, monthIndex0: 5 });
  });

  it("offers no return when already on the current month", () => {
    renderNav(5);
    expect(screen.getByRole("button", { name: "Today" })).toBeDisabled();
  });

  it("jumps to a chosen month via the picker", async () => {
    const onMonthChange = vi.fn();
    render(
      <MonthNav
        month={{ year: 2026, monthIndex0: 5 }}
        activeMonths={["2025-06"]}
        onMonthChange={onMonthChange}
      />,
    );
    await user.click(screen.getByRole("button", { name: "June 2026" }));
    await user.click(screen.getByRole("button", { name: "Previous year" }));
    await user.click(screen.getByRole("button", { name: "Jun 2025" }));
    expect(onMonthChange).toHaveBeenCalledWith({ year: 2025, monthIndex0: 5 });
  });
});
