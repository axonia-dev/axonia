import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";

import { TextInput } from "@/components/ui/TextInput";

let user: ReturnType<typeof userEvent.setup>;

beforeEach(() => {
  user = userEvent.setup({ delay: null });
});

describe("TextInput", () => {
  it("exposes its label to assistive tech", () => {
    // when
    render(<TextInput label="Name" value="" onChange={vi.fn()} />);

    // then
    expect(screen.getByLabelText("Name")).toBeInTheDocument();
  });

  it("calls onChange with the typed value", async () => {
    // given
    const onChange = vi.fn();
    render(<TextInput label="Name" value="" onChange={onChange} />);

    // when
    await user.type(screen.getByLabelText("Name"), "g");

    // then
    expect(onChange).toHaveBeenCalledWith("g");
  });

  it("renders a date input when type=date", () => {
    render(
      <TextInput
        label="Starts on"
        value="2026-06-15"
        onChange={() => {}}
        type="date"
      />,
    );
    expect(screen.getByLabelText("Starts on")).toHaveAttribute("type", "date");
  });

  /**
   * The only masking this component offers, and it covers characters the reader just typed. A
   * field with no `type` is unmasked, which is what keeps every other caller reading as it always
   * did rather than silently hiding a value somebody has to check.
   */
  it("masks what is typed when type=password", () => {
    render(
      <TextInput
        label="Client secret"
        value=""
        onChange={vi.fn()}
        type="password"
      />,
    );

    expect(screen.getByLabelText("Client secret")).toHaveAttribute(
      "type",
      "password",
    );
  });

  it("leaves a field with no type unmasked", () => {
    render(<TextInput label="Name" value="" onChange={vi.fn()} />);

    expect(screen.getByLabelText("Name")).toHaveAttribute("type", "text");
  });

  /**
   * A description rendered as a bare paragraph is decoration: react-aria only wires
   * `aria-describedby` for its own slotted `Text`, so without the slot a screen reader hears the
   * label and nothing else. Every consumer of this prop puts a fact in it that the label alone
   * does not carry, which is exactly the kind of fact that must not be sighted-only.
   */
  it("exposes its description to assistive tech, not only on screen", () => {
    // when
    render(
      <TextInput
        label="Display name"
        value=""
        onChange={vi.fn()}
        description="Shown on every card this member appears on"
      />,
    );

    // then
    expect(screen.getByLabelText("Display name")).toHaveAccessibleDescription(
      "Shown on every card this member appears on",
    );
  });

  /**
   * A mark drawn inside the field, and nothing more: it repeats what the label already says, so
   * announcing it would make a screen reader read the same field twice.
   */
  it("draws an icon inside the field without announcing it", () => {
    // when
    render(
      <TextInput
        label="Location"
        value=""
        onChange={vi.fn()}
        icon={<svg data-testid="pin" />}
      />,
    );

    // then
    expect(screen.getByTestId("pin")).toBeInTheDocument();
    expect(screen.getByLabelText("Location")).toHaveAccessibleName("Location");
    expect(screen.getByLabelText("Location").className).toContain("pl-9");
  });

  it("leaves the field's own padding alone when there is no icon", () => {
    // when
    render(<TextInput label="Location" value="" onChange={vi.fn()} />);

    // then
    expect(screen.getByLabelText("Location").className).not.toContain("pl-9");
  });

  it("renders an error message as an alert when provided", () => {
    // when
    render(
      <TextInput
        label="Name"
        value=""
        onChange={vi.fn()}
        errorMessage="Name is required"
      />,
    );

    // then
    expect(screen.getByRole("alert")).toHaveTextContent("Name is required");
  });

  it("ties its error to the input, as it ties the description", () => {
    // when
    render(
      <TextInput
        label="Name"
        value=""
        onChange={vi.fn()}
        errorMessage="Name is required"
      />,
    );

    // then
    const input = screen.getByLabelText("Name");
    expect(input).toBeInvalid();
    expect(input).toHaveAccessibleDescription("Name is required");
  });
});
