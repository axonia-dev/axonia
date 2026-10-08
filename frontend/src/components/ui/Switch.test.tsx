import { fireEvent, render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";

import { Switch } from "@/components/ui/Switch";

let user: ReturnType<typeof userEvent.setup>;

beforeEach(() => {
  user = userEvent.setup({ delay: null });
});

describe("Switch", () => {
  it("renders the label and description", () => {
    // given
    render(
      <Switch
        label="Members can start a series"
        description="Any member may start a new series."
      />,
    );

    // then
    expect(
      screen.getByRole("switch", { name: /members can start a series/i }),
    ).toBeInTheDocument();
    expect(
      screen.getByText(/any member may start a new series/i),
    ).toBeInTheDocument();
  });

  it("reflects isSelected", () => {
    // given
    render(<Switch label="Toggle" isSelected onChange={() => {}} />);

    // then
    expect(screen.getByRole("switch")).toBeChecked();
  });

  it("fires onChange with the next value", async () => {
    // given
    const onChange = vi.fn();
    render(<Switch label="Toggle" isSelected={false} onChange={onChange} />);

    // when
    await user.click(screen.getByRole("switch"));

    // then
    expect(onChange).toHaveBeenCalledWith(true);
  });

  it("takes its accessible name from external text via aria-labelledby, rendering that text only once", () => {
    // given
    render(
      <>
        <span id="row-label">Session reminders</span>
        <Switch aria-labelledby="row-label" />
      </>,
    );

    // then
    expect(
      screen.getByRole("switch", { name: "Session reminders" }),
    ).toBeInTheDocument();
    expect(screen.getAllByText("Session reminders")).toHaveLength(1);
  });

  it("pulses its track when asked, and tells the caller when the pulse is over", () => {
    // given
    const onPulseEnd = vi.fn();
    const { container } = render(
      <Switch label="Manage members" isPulsing onPulseEnd={onPulseEnd} />,
    );
    const track = container.querySelector("[data-slot='switch-track']");

    // then
    expect(track?.className).toContain("animate-[policy-pulse_500ms_ease-out]");

    // when
    fireEvent.animationEnd(track as Element);

    // then
    expect(onPulseEnd).toHaveBeenCalledOnce();
  });

  it("does not pulse by default", () => {
    // given
    const { container } = render(<Switch label="Manage members" />);

    // then
    expect(
      container.querySelector("[data-slot='switch-track']")?.className,
    ).not.toContain("policy-pulse");
  });
});
