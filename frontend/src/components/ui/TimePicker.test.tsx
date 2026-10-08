import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import type { ReactNode } from "react";
import { I18nProvider } from "react-aria-components";
import { beforeEach, describe, expect, it, vi } from "vitest";

import { TimePicker } from "@/components/ui/TimePicker";

let user: ReturnType<typeof userEvent.setup>;

beforeEach(() => {
  user = userEvent.setup({ delay: null });
});

function renderUS(ui: ReactNode) {
  return render(<I18nProvider locale="en-US">{ui}</I18nProvider>);
}

describe("TimePicker", () => {
  it("shows a provided ISO time in 12-hour display", () => {
    renderUS(
      <TimePicker label="Preferred time" value="18:00:00" onChange={vi.fn()} />,
    );
    expect(screen.getByText("PM")).toBeInTheDocument();
    expect(screen.getByRole("spinbutton", { name: /hour/i })).toHaveAttribute(
      "aria-valuenow",
      "6",
    );
  });

  it("emits an updated ISO time when a segment changes", async () => {
    const onChange = vi.fn();
    renderUS(
      <TimePicker
        label="Preferred time"
        value="18:00:00"
        onChange={onChange}
      />,
    );
    await user.click(screen.getByRole("spinbutton", { name: /minute/i }));
    await user.keyboard("{ArrowUp}");
    expect(onChange).toHaveBeenLastCalledWith("18:01:00");
  });

  /**
   * A refusal a caller works out for itself - a bound on more than this one field - reaching both
   * halves of the signal: the segments say so to assistive tech, and the group carries the state
   * the border is drawn from. Absent means the caller has no opinion, and the field keeps its own.
   */
  it("marks its segments and its border when a caller refuses the value", () => {
    const { rerender } = renderUS(
      <TimePicker label="Preferred time" value="18:00:00" onChange={vi.fn()} />,
    );
    const field = () => screen.getByRole("group", { name: "Preferred time" });

    expect(field()).not.toHaveAttribute("data-invalid");
    expect(
      screen.getByRole("spinbutton", { name: /hour/i }),
    ).not.toHaveAttribute("aria-invalid", "true");

    rerender(
      <I18nProvider locale="en-US">
        <TimePicker
          label="Preferred time"
          value="18:00:00"
          onChange={vi.fn()}
          isInvalid
        />
      </I18nProvider>,
    );

    expect(field()).toHaveAttribute("data-invalid");
    expect(screen.getByRole("spinbutton", { name: /hour/i })).toHaveAttribute(
      "aria-invalid",
      "true",
    );
  });
});
