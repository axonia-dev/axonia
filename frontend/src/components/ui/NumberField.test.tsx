import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";

import { NumberField } from "@/components/ui/NumberField";

let user: ReturnType<typeof userEvent.setup>;

beforeEach(() => {
  user = userEvent.setup({ delay: null });
});

describe("NumberField", () => {
  it("labels the control and shows its unit", () => {
    render(
      <NumberField
        label="Days of notice before a session"
        unit="days"
        value="3"
        onChange={() => {}}
        minValue={0}
        maxValue={30}
      />,
    );

    expect(
      screen.getByLabelText("Days of notice before a session"),
    ).toHaveValue("3");
    expect(screen.getByText("days")).toBeVisible();
  });

  it("reports typed digits as a string", async () => {
    const onChange = vi.fn();
    render(
      <NumberField
        label="Days of notice before a session"
        unit="days"
        value=""
        onChange={onChange}
        minValue={0}
        maxValue={30}
      />,
    );

    const input = screen.getByLabelText("Days of notice before a session");
    await user.type(input, "5");
    await user.tab();

    expect(onChange).toHaveBeenCalledWith("5");
  });

  it("reports a cleared field as an empty string, not 0 or NaN", async () => {
    const onChange = vi.fn();
    render(
      <NumberField
        label="Days of notice before a session"
        unit="days"
        value="3"
        onChange={onChange}
        minValue={0}
        maxValue={30}
      />,
    );

    const input = screen.getByLabelText("Days of notice before a session");
    await user.clear(input);
    await user.tab();

    expect(onChange).toHaveBeenCalledWith("");
  });

  it("shows an error message and marks the control invalid", () => {
    render(
      <NumberField
        label="Days of notice before a session"
        unit="days"
        value="99"
        onChange={() => {}}
        minValue={0}
        maxValue={30}
        errorMessage="Must be 30 or fewer"
      />,
    );

    expect(screen.getByRole("alert")).toHaveTextContent("Must be 30 or fewer");
    expect(screen.getByRole("group")).toHaveAttribute("aria-invalid", "true");
  });

  it("carries the unit in its accessible name for a realistic caller", () => {
    render(
      <NumberField
        label="Hours before the wrap-up sends"
        unit="hours"
        value="2"
        onChange={() => {}}
        minValue={0}
        maxValue={24}
      />,
    );

    expect(screen.getByRole("textbox", { name: /hours/i })).toBeInTheDocument();
  });

  it("lets an over-max value survive commit uncorrected, for the caller to flag", async () => {
    // given - the default react-aria commit behavior ("snap") would silently
    // rewrite 99 to 30; this field must not, so the caller's own bound message
    // can state the violation instead of the control fixing it in silence
    const onChange = vi.fn();
    render(
      <NumberField
        label="Days of notice before a session"
        unit="days"
        value="3"
        onChange={onChange}
        minValue={0}
        maxValue={30}
      />,
    );

    const input = screen.getByLabelText("Days of notice before a session");
    await user.clear(input);
    await user.type(input, "99");
    await user.tab();

    expect(onChange).toHaveBeenLastCalledWith("99");
  });

  it("lets a pasted under-min value survive commit uncorrected, for the caller to flag", async () => {
    // given - typing a minus sign is rejected outright when minValue is 0 or
    // more, but a paste bypasses that per-keystroke filter; the min bound must
    // be statable through the one path that can actually reach it
    const onChange = vi.fn();
    render(
      <NumberField
        label="Days of notice before a session"
        unit="days"
        value="3"
        onChange={onChange}
        minValue={0}
        maxValue={30}
      />,
    );

    const input = screen.getByLabelText("Days of notice before a session");
    await user.clear(input);
    await user.paste("-5");

    expect(onChange).toHaveBeenLastCalledWith("-5");
  });

  it("lets a fractional value survive commit uncorrected, for the caller to flag", async () => {
    const onChange = vi.fn();
    render(
      <NumberField
        label="Days of notice before a session"
        unit="days"
        value="3"
        onChange={onChange}
        minValue={0}
        maxValue={30}
      />,
    );

    const input = screen.getByLabelText("Days of notice before a session");
    await user.clear(input);
    await user.type(input, "3.5");
    await user.tab();

    expect(onChange).toHaveBeenLastCalledWith("3.5");
  });
});
