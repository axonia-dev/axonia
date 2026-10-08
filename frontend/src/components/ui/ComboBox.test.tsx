import { fireEvent, render, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

import { ComboBox } from "@/components/ui/ComboBox";

let user: ReturnType<typeof userEvent.setup>;

beforeEach(() => {
  user = userEvent.setup({ delay: null });
});

afterEach(() => {
  vi.unstubAllGlobals();
});

const options = [
  { id: "b7c1", label: "Platform Engineering" },
  { id: "3f9a", label: "Security Guild" },
];

describe("ComboBox", () => {
  it("reports typed text as a literal value", async () => {
    const onInputChange = vi.fn();
    render(
      <ComboBox
        label="Group"
        value=""
        options={options}
        onInputChange={onInputChange}
        onSelect={vi.fn()}
      />,
    );
    await user.type(
      screen.getByRole("combobox", { name: "Group" }),
      "data-leads",
    );
    expect(onInputChange).toHaveBeenLastCalledWith("data-leads");
  });

  it("reports a picked suggestion with its underlying id", async () => {
    const onSelect = vi.fn();
    render(
      <ComboBox
        label="Group"
        value="Plat"
        options={options}
        onInputChange={vi.fn()}
        onSelect={onSelect}
      />,
    );
    await user.click(screen.getByRole("combobox", { name: "Group" }));
    await user.click(
      await screen.findByRole("option", { name: "Platform Engineering" }),
    );
    expect(onSelect).toHaveBeenCalledWith({
      id: "b7c1",
      label: "Platform Engineering",
    });
  });

  it("does not report the picked suggestion's label as a typed value", async () => {
    const onInputChange = vi.fn();
    const onSelect = vi.fn();
    render(
      <ComboBox
        label="Group"
        value="Plat"
        options={options}
        onInputChange={onInputChange}
        onSelect={onSelect}
      />,
    );
    await user.click(screen.getByRole("combobox", { name: "Group" }));
    await user.click(
      await screen.findByRole("option", { name: "Platform Engineering" }),
    );
    expect(onSelect).toHaveBeenCalledWith({
      id: "b7c1",
      label: "Platform Engineering",
    });
    expect(onInputChange).not.toHaveBeenCalled();
  });

  /** A country list is 229 options long. Rendered without a scroll container it drew 8000px of
   *  options through a box that could not clip them, over the page and past the viewport, and the
   *  wheel scrolled the page instead of the list. The height cap that goes with this is measured
   *  by the overlay at runtime, so only the clipping is assertable without layout. */
  it("keeps the suggestion list inside a scroll container", async () => {
    render(
      <ComboBox
        label="Group"
        value=""
        options={options}
        onInputChange={vi.fn()}
        onSelect={vi.fn()}
      />,
    );
    await user.click(screen.getByRole("combobox", { name: "Group" }));

    const popover = (await screen.findByRole("listbox")).parentElement;
    expect(popover).toHaveClass("overflow-y-auto");
  });

  /** Typing into a field that already holds a value appended to it, and the control filters on
   *  what the field holds: "Switzerland" + "ger" matches nothing, which reads as a dead control. */
  it("replaces the value already in the field with what is typed next", async () => {
    const onInputChange = vi.fn();
    render(
      <ComboBox
        label="Group"
        value="Platform Engineering"
        options={options}
        onInputChange={onInputChange}
        onSelect={vi.fn()}
      />,
    );
    await user.click(screen.getByRole("combobox", { name: "Group" }));
    await user.keyboard("Sec");

    expect(onInputChange).toHaveBeenLastCalledWith("Sec");
  });

  /** A required, backend-validated field can only ever hold a value the caller committed via
   *  onSelect. Leaving typed-but-unselected text on screen after the field loses focus would show
   *  something other than what a submit actually sends.
   *
   *  While the suggestion popover is open, react-aria hides the rest of the page from the
   *  accessibility tree, so a role-based query can't target anything to click "elsewhere" -
   *  firing `blur` on the input directly is the same signal a real focus change sends. */
  it("reverts to the committed value when typed text is left unselected on blur", async () => {
    const onSelect = vi.fn();
    const onInputChange = vi.fn();
    render(
      <ComboBox
        label="Group"
        value="Plat"
        options={options}
        onInputChange={onInputChange}
        onSelect={onSelect}
      />,
    );
    const input = screen.getByRole("combobox", { name: "Group" });
    await user.click(input);
    await user.keyboard("Sec");
    expect(input).toHaveValue("Sec");

    fireEvent.blur(input);

    expect(input).toHaveValue("Plat");
    expect(onInputChange).toHaveBeenLastCalledWith("Plat");
    expect(onSelect).not.toHaveBeenCalled();
  });

  /** A plain consumer (no serverFiltered, no statusMessage) never opts into an empty collection:
   *  text matching nothing should show no popover at all, not an empty bordered strip. */
  it("shows no suggestion popover when typed text matches nothing", async () => {
    render(
      <ComboBox
        label="Group"
        value=""
        options={options}
        onInputChange={vi.fn()}
        onSelect={vi.fn()}
      />,
    );
    await user.type(screen.getByRole("combobox", { name: "Group" }), "zzz");

    expect(screen.queryByRole("listbox")).not.toBeInTheDocument();
  });

  it("announces a search status shown in place of suggestions", async () => {
    render(
      <ComboBox
        label="Contributor"
        value=""
        options={[]}
        onInputChange={vi.fn()}
        onSelect={vi.fn()}
        serverFiltered
        statusMessage="No members found"
      />,
    );
    await user.click(screen.getByRole("combobox", { name: "Contributor" }));

    expect(await screen.findByRole("status")).toHaveTextContent(
      "No members found",
    );
  });

  /** Highlighting an option re-dispatches a synthetic focus on the input, same as a pick does; that
   *  synthetic focus must not run the real-refocus select() logic, or the next keystroke would wipe
   *  out everything typed before ArrowDown instead of extending it. */
  it("keeps typed text across arrow-key navigation", async () => {
    const onInputChange = vi.fn();
    render(
      <ComboBox
        label="Group"
        value=""
        options={options}
        onInputChange={onInputChange}
        onSelect={vi.fn()}
      />,
    );
    const combobox = screen.getByRole("combobox", { name: "Group" });
    await user.click(combobox);
    await user.keyboard("Sec");
    await user.keyboard("{ArrowDown}");
    await user.keyboard("urity");

    expect(combobox).toHaveValue("Security");
  });

  it("closes on Escape even right after arrow-key navigation", async () => {
    render(
      <ComboBox
        label="Group"
        value=""
        options={options}
        onInputChange={vi.fn()}
        onSelect={vi.fn()}
      />,
    );
    const combobox = screen.getByRole("combobox", { name: "Group" });
    await user.click(combobox);
    await user.keyboard("P");
    await user.keyboard("{ArrowDown}");
    await user.keyboard("{Escape}");

    expect(screen.queryByRole("listbox")).not.toBeInTheDocument();
  });

  it("closes the suggestion popover after picking an option, so other page controls stay reachable", async () => {
    render(
      <ComboBox
        label="Group"
        value="Plat"
        options={options}
        onInputChange={vi.fn()}
        onSelect={vi.fn()}
      />,
    );
    await user.click(screen.getByRole("combobox", { name: "Group" }));
    await user.click(
      await screen.findByRole("option", { name: "Platform Engineering" }),
    );
    expect(screen.queryByRole("option")).not.toBeInTheDocument();
  });

  it("links the error message to the field it describes", () => {
    // when
    render(
      <ComboBox
        label="Group"
        value=""
        options={options}
        onInputChange={vi.fn()}
        onSelect={vi.fn()}
        errorMessage="At most 20 areas per community."
      />,
    );

    // then
    const combobox = screen.getByRole("combobox", { name: "Group" });
    expect(combobox).toHaveAttribute("aria-invalid", "true");
    expect(combobox).toHaveAccessibleDescription(
      "At most 20 areas per community.",
    );
    expect(screen.getByRole("alert")).toHaveTextContent(
      "At most 20 areas per community.",
    );
  });

  it.each([
    [undefined, false],
    ["code", true],
  ] as const)(
    "sets a hint in monospace only for code hints (hintStyle %s)",
    async (hintStyle, mono) => {
      // given
      render(
        <ComboBox
          label="Group"
          value=""
          options={[{ id: "b7c1", label: "Platform", hint: "b7c1" }]}
          onInputChange={vi.fn()}
          onSelect={vi.fn()}
          {...(hintStyle === undefined ? {} : { hintStyle })}
        />,
      );

      // when
      await user.click(screen.getByRole("combobox", { name: "Group" }));

      // then
      const hint = within(await screen.findByRole("option")).getByText("b7c1");
      expect(hint.classList.contains("font-mono")).toBe(mono);
    },
  );
});
