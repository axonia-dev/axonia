import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";

import { TextArea } from "@/components/ui/TextArea";

let user: ReturnType<typeof userEvent.setup>;

beforeEach(() => {
  user = userEvent.setup({ delay: null });
});

describe("TextArea", () => {
  it("exposes its label to assistive tech", () => {
    // when
    render(<TextArea label="Description" value="" onChange={vi.fn()} />);

    // then
    expect(screen.getByLabelText("Description")).toBeInTheDocument();
  });

  it("calls onChange with the typed value", async () => {
    // given
    const onChange = vi.fn();
    render(<TextArea label="Description" value="" onChange={onChange} />);

    // when
    await user.type(screen.getByLabelText("Description"), "x");

    // then
    expect(onChange).toHaveBeenCalledWith("x");
  });

  /**
   * A mark drawn inside the box, and nothing more: it repeats what the label already says, so
   * announcing it would make a screen reader read the same field twice.
   */
  it("draws an icon inside the box without announcing it", () => {
    // when
    render(
      <TextArea
        label="Joining details"
        value=""
        onChange={vi.fn()}
        icon={<svg data-testid="key" />}
      />,
    );

    // then
    expect(screen.getByTestId("key")).toBeInTheDocument();
    expect(screen.getByLabelText("Joining details")).toHaveAccessibleName(
      "Joining details",
    );
    expect(screen.getByLabelText("Joining details").className).toContain(
      "pl-9",
    );
  });

  /**
   * The same slotted description TextInput carries, for the same reason: a bare paragraph is not
   * wired into `aria-describedby`, so the one rule the label cannot state would be sighted-only.
   */
  it("exposes its description to assistive tech, not only on screen", () => {
    // when
    render(
      <TextArea
        label="Joining details"
        value=""
        onChange={vi.fn()}
        description="All three empty means the series' standing place."
      />,
    );

    // then
    expect(
      screen.getByLabelText("Joining details"),
    ).toHaveAccessibleDescription(
      "All three empty means the series' standing place.",
    );
  });

  it("caps input at maxLength when provided", () => {
    // when
    render(
      <TextArea
        label="Description"
        value=""
        onChange={vi.fn()}
        maxLength={10}
      />,
    );

    // then
    expect(screen.getByLabelText("Description")).toHaveAttribute(
      "maxLength",
      "10",
    );
  });
});
