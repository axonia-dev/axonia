import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { useState } from "react";
import { beforeEach, describe, expect, it, vi } from "vitest";

import { ComboBox, type ComboBoxOption } from "@/components/ui/ComboBox";

let user: ReturnType<typeof userEvent.setup>;

beforeEach(() => {
  user = userEvent.setup({ delay: null });
});

const options = [
  { id: "b7c1", label: "Platform Engineering" },
  { id: "3f9a", label: "Security Guild" },
];

function PickingComboBox() {
  const [value, setValue] = useState("");
  return (
    <ComboBox
      label="Group"
      value={value}
      options={options}
      onInputChange={vi.fn()}
      onSelect={(option: ComboBoxOption) => setValue(option.label)}
    />
  );
}

/** `resetTo` stands in for a value the caller sets later on its own, with focus still in the field. */
function ExternallyReset({
  applyPick,
  resetTo,
}: Readonly<{ applyPick: boolean; resetTo?: string }>) {
  const [picked, setPicked] = useState("");
  return (
    <ComboBox
      label="Group"
      value={resetTo ?? picked}
      options={options}
      onInputChange={vi.fn()}
      onSelect={(option: ComboBoxOption) => {
        if (applyPick) setPicked(option.label);
      }}
    />
  );
}

describe("ComboBox clicked again after a pick", () => {
  /** A pick leaves focus in the field, so clicking back into it fires no focus event: without
   *  selecting the picked text there, typing appends to it and matches nothing. */
  it("replaces a text picked with the mouse when the field is clicked and typed into", async () => {
    render(<PickingComboBox />);
    const combobox = screen.getByRole("combobox", { name: "Group" });
    await user.click(combobox);
    await user.keyboard("Sec");
    await user.click(
      await screen.findByRole("option", { name: "Security Guild" }),
    );
    expect(combobox).toHaveValue("Security Guild");

    await user.click(combobox);
    await user.keyboard("Plat");

    expect(combobox).toHaveValue("Plat");
  });

  it("replaces a text picked with the keyboard when the field is clicked and typed into", async () => {
    render(<PickingComboBox />);
    const combobox = screen.getByRole("combobox", { name: "Group" });
    await user.click(combobox);
    await user.keyboard("Sec{ArrowDown}{Enter}");
    expect(combobox).toHaveValue("Security Guild");

    await user.click(combobox);
    await user.keyboard("Plat");

    expect(combobox).toHaveValue("Plat");
  });

  it("places the caret on a second click after a pick", async () => {
    render(<PickingComboBox />);
    const combobox = screen.getByRole("combobox", { name: "Group" });
    await user.click(combobox);
    await user.keyboard("Sec{ArrowDown}{Enter}");
    await user.click(combobox);

    await user.click(combobox);
    await user.keyboard("!");

    expect(combobox).toHaveValue("Security Guild!");
  });

  it("keeps a text range dragged over after a pick", async () => {
    // given
    render(<PickingComboBox />);
    const combobox = screen.getByRole<HTMLInputElement>("combobox", {
      name: "Group",
    });
    await user.click(combobox);
    await user.keyboard("Sec{ArrowDown}{Enter}");

    // when
    await user.pointer([
      { keys: "[MouseLeft>]", target: combobox, offset: 0 },
      { offset: 3 },
      { keys: "[/MouseLeft]" },
    ]);
    await user.keyboard("X");

    // then
    expect(combobox).toHaveValue("Xurity Guild");
  });

  it.each([
    ["applied the picked label", true],
    ["kept the value", false],
  ])(
    "places the caret once the value changed from outside after a pick that %s",
    async (_, applyPick) => {
      // given
      const { rerender } = render(<ExternallyReset applyPick={applyPick} />);
      const combobox = screen.getByRole("combobox", { name: "Group" });
      await user.click(combobox);
      await user.keyboard("Sec{ArrowDown}{Enter}");
      rerender(
        <ExternallyReset
          applyPick={applyPick}
          resetTo="Platform Engineering"
        />,
      );

      // when
      await user.click(combobox);
      await user.keyboard("!");

      // then
      expect(combobox).toHaveValue("Platform Engineering!");
    },
  );
});

describe("ComboBox at a narrow width", () => {
  it("ends a placeholder too long for the field with an ellipsis rather than a clipped letter", () => {
    render(
      <ComboBox
        label="Group"
        value=""
        placeholder="Add an area, e.g. kubernetes"
        options={options}
        onInputChange={vi.fn()}
        onSelect={vi.fn()}
      />,
    );
    expect(screen.getByRole("combobox", { name: "Group" })).toHaveClass(
      "text-ellipsis",
    );
  });
});
