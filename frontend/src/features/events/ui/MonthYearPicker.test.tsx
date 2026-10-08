import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

import { MonthYearPicker } from "@/features/events/ui/MonthYearPicker";

beforeEach(() => {
  vi.useFakeTimers({ toFake: ["Date"] });
  vi.setSystemTime(new Date("2026-06-14T12:00:00Z"));
});
afterEach(() => vi.useRealTimers());

function open() {
  return userEvent.setup({ delay: null });
}

describe("MonthYearPicker", () => {
  it("opens and lets you pick a month in a stepped-to year", async () => {
    const user = open();
    const onSelect = vi.fn();
    render(
      <MonthYearPicker
        year={2026}
        monthIndex0={6}
        activeMonths={["2024-06"]}
        onSelect={onSelect}
      />,
    );

    await user.click(screen.getByRole("button", { name: "July 2026" }));
    await user.click(screen.getByRole("button", { name: "Previous year" }));
    await user.click(screen.getByRole("button", { name: "Previous year" }));
    await user.click(screen.getByRole("button", { name: "Jun 2024" }));

    expect(onSelect).toHaveBeenCalledWith(2024, 5);
  });

  it("steps back into a year with no activity, so past sessions can be backfilled", async () => {
    const user = open();
    const onSelect = vi.fn();
    render(
      <MonthYearPicker
        year={2026}
        monthIndex0={6}
        activeMonths={["2026-07"]}
        onSelect={onSelect}
      />,
    );

    await user.click(screen.getByRole("button", { name: "July 2026" }));
    await user.click(screen.getByRole("button", { name: "Previous year" }));
    await user.click(screen.getByRole("button", { name: "Mar 2025" }));

    expect(onSelect).toHaveBeenCalledWith(2025, 2);
  });

  it("steps forward into a year with no activity, so future sessions can be planned", async () => {
    const user = open();
    const onSelect = vi.fn();
    render(
      <MonthYearPicker
        year={2026}
        monthIndex0={6}
        activeMonths={["2026-07"]}
        onSelect={onSelect}
      />,
    );

    await user.click(screen.getByRole("button", { name: "July 2026" }));
    await user.click(screen.getByRole("button", { name: "Next year" }));
    await user.click(screen.getByRole("button", { name: "Jan 2027" }));

    expect(onSelect).toHaveBeenCalledWith(2027, 0);
  });

  it("dots only the months that have activity", async () => {
    const user = open();
    render(
      <MonthYearPicker
        year={2026}
        monthIndex0={0}
        activeMonths={["2026-07"]}
        onSelect={vi.fn()}
      />,
    );

    await user.click(screen.getByRole("button", { name: "January 2026" }));
    expect(screen.getByRole("button", { name: "Jul 2026" })).toContainHTML(
      "rounded-full",
    );
    expect(screen.getByRole("button", { name: "Aug 2026" })).not.toContainHTML(
      "rounded-full",
    );
  });
});
