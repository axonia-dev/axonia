import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import type { ReactNode } from "react";
import { I18nProvider } from "react-aria-components";
import { beforeEach, describe, expect, it, vi } from "vitest";

import { DatePicker } from "@/components/ui/DatePicker";

let user: ReturnType<typeof userEvent.setup>;

beforeEach(() => {
  user = userEvent.setup({ delay: null });
});

function renderUS(ui: ReactNode) {
  return render(<I18nProvider locale="en-US">{ui}</I18nProvider>);
}

describe("DatePicker", () => {
  it("shows a provided ISO date in its segments", () => {
    renderUS(
      <DatePicker label="Starts on" value="2026-06-15" onChange={vi.fn()} />,
    );
    expect(screen.getByRole("spinbutton", { name: /year/i })).toHaveAttribute(
      "aria-valuenow",
      "2026",
    );
    expect(screen.getByRole("spinbutton", { name: /month/i })).toHaveAttribute(
      "aria-valuenow",
      "6",
    );
  });

  it("emits an updated ISO date when a segment changes", async () => {
    const onChange = vi.fn();
    renderUS(
      <DatePicker label="Starts on" value="2026-06-15" onChange={onChange} />,
    );
    await user.click(screen.getByRole("spinbutton", { name: /day/i }));
    await user.keyboard("{ArrowUp}");
    expect(onChange).toHaveBeenLastCalledWith("2026-06-16");
  });

  it("exposes a themed calendar trigger", () => {
    renderUS(<DatePicker label="Starts on" value="" onChange={vi.fn()} />);
    expect(
      screen.getByRole("button", { name: /open calendar/i }),
    ).toBeInTheDocument();
  });

  /** minValue is the mechanism `CreateTokenDialog` and `DirectorySyncPage` rely on so a bearer
   *  credential's expiry cannot even be picked in the past, not only refused after the fact. */
  it("disables a calendar day before minValue", async () => {
    renderUS(
      <DatePicker
        label="Expires on"
        value="2026-08-20"
        onChange={vi.fn()}
        minValue="2026-08-15"
      />,
    );
    await user.click(screen.getByRole("button", { name: /open calendar/i }));
    const dayCells = screen.getAllByRole("button");
    const dayCell = (day: string) =>
      dayCells.find((cell) => cell.textContent === day);
    expect(dayCell("10")).toHaveAttribute("aria-disabled", "true");
    expect(dayCell("20")).not.toHaveAttribute("aria-disabled");
  });

  /**
   * A refusal a caller works out for itself - a bound on more than this one field - reaching both
   * halves of the signal: the segments say so to assistive tech, and the group carries the state
   * the border is drawn from. Absent means the caller has no opinion, and the field keeps its own.
   */
  it("marks its segments and its border when a caller refuses the value", () => {
    const { rerender } = renderUS(
      <DatePicker label="Starts on" value="2026-06-15" onChange={vi.fn()} />,
    );
    const field = () => screen.getByRole("group", { name: "Starts on" });

    expect(field()).not.toHaveAttribute("data-invalid");
    expect(
      screen.getByRole("spinbutton", { name: /month/i }),
    ).not.toHaveAttribute("aria-invalid", "true");

    rerender(
      <I18nProvider locale="en-US">
        <DatePicker
          label="Starts on"
          value="2026-06-15"
          onChange={vi.fn()}
          isInvalid
        />
      </I18nProvider>,
    );

    expect(field()).toHaveAttribute("data-invalid");
    expect(screen.getByRole("spinbutton", { name: /month/i })).toHaveAttribute(
      "aria-invalid",
      "true",
    );
  });
});
