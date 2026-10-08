import { fireEvent, render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import type { ComponentProps } from "react";
import { beforeEach, describe, expect, it, vi } from "vitest";

import { AddTagField } from "@/components/patterns/AddTagField";

let user: ReturnType<typeof userEvent.setup>;
let onAdd: ReturnType<typeof vi.fn<(tag: string, done: () => void) => void>>;

const OPTIONS = [
  { id: "kafka", label: "kafka", hint: "4 people" },
  { id: "terraform", label: "terraform", hint: "1 person" },
];

function renderField(props: Partial<ComponentProps<typeof AddTagField>> = {}) {
  return render(
    <AddTagField
      label="Add an area"
      placeholder="Add an area, e.g. terraform"
      options={OPTIONS}
      isPending={false}
      onAdd={onAdd}
      {...props}
    />,
  );
}

beforeEach(() => {
  user = userEvent.setup({ delay: null });
  onAdd = vi.fn();
});

describe("AddTagField", () => {
  it("adds the typed text with the Add button", async () => {
    // given
    renderField();
    await user.type(screen.getByRole("combobox"), "Rust");

    // when
    await user.click(screen.getByRole("button", { name: "Add" }));

    // then
    expect(onAdd).toHaveBeenCalledOnce();
    expect(onAdd.mock.calls[0]?.[0]).toBe("Rust");
  });

  it("adds the typed text on Enter when nothing is suggested", async () => {
    // when
    renderField();
    await user.type(screen.getByRole("combobox"), "Rust{Enter}");

    // then
    expect(onAdd).toHaveBeenCalledOnce();
    expect(onAdd).toHaveBeenCalledWith("Rust", expect.any(Function));
  });

  it("adds the typed text on Enter while suggestions are showing", async () => {
    // given
    renderField();
    await user.type(screen.getByRole("combobox"), "terraf");
    await screen.findByRole("option", { name: /terraform/ });

    // when
    await user.keyboard("{Enter}");

    // then
    expect(onAdd).toHaveBeenCalledOnce();
    expect(onAdd).toHaveBeenCalledWith("terraf", expect.any(Function));
  });

  it("adds a fully typed suggestion once on Enter", async () => {
    // given
    renderField();
    await user.type(screen.getByRole("combobox"), "terraform");
    await screen.findByRole("option", { name: /terraform/ });

    // when
    await user.keyboard("{Enter}");

    // then
    expect(onAdd).toHaveBeenCalledOnce();
    expect(onAdd).toHaveBeenCalledWith("terraform", expect.any(Function));
  });

  it("adds the suggestion highlighted with the arrow keys, not the typed text", async () => {
    // given
    renderField();
    await user.type(screen.getByRole("combobox"), "ka");
    await screen.findByRole("option", { name: /kafka/ });

    // when
    await user.keyboard("{ArrowDown}{Enter}");

    // then
    expect(onAdd).toHaveBeenCalledOnce();
    expect(onAdd).toHaveBeenCalledWith("kafka", expect.any(Function));
  });

  it("adds a suggestion picked with the pointer by its id", async () => {
    // given
    renderField();
    await user.type(screen.getByRole("combobox"), "ka");

    // when
    await user.click(await screen.findByRole("option", { name: /kafka/ }));

    // then
    expect(onAdd).toHaveBeenCalledWith("kafka", expect.any(Function));
  });

  it("suggests only the options that match the text", async () => {
    // given
    renderField();

    // when
    await user.type(screen.getByRole("combobox"), "ka");

    // then
    expect(
      await screen.findByRole("option", { name: /kafka/ }),
    ).toBeInTheDocument();
    expect(
      screen.queryByRole("option", { name: /terraform/ }),
    ).not.toBeInTheDocument();
  });

  it.each([
    ["flagged as composing", { isComposing: true }],
    ["flagged by Safari's keyCode 229 only", { keyCode: 229 }],
  ])(
    "leaves an IME composition to finish when Enter confirms it (%s)",
    async (_, composition) => {
      // given
      renderField();
      const input = screen.getByRole("combobox");
      await user.type(input, "kafk");

      // when
      fireEvent.keyDown(input, { key: "Enter", ...composition });

      // then
      expect(onAdd).not.toHaveBeenCalled();
    },
  );

  it("clears the text once the add is done", async () => {
    // given
    onAdd.mockImplementation((_tag, done) => done());
    renderField();
    await user.type(screen.getByRole("combobox"), "Rust");

    // when
    await user.click(screen.getByRole("button", { name: "Add" }));

    // then
    expect(screen.getByRole("combobox")).toHaveValue("");
  });

  it("keeps the text until the add is done", async () => {
    // given
    renderField();
    await user.type(screen.getByRole("combobox"), "Rust");

    // when
    await user.click(screen.getByRole("button", { name: "Add" }));

    // then
    expect(screen.getByRole("combobox")).toHaveValue("Rust");
  });

  it.each([
    ["an empty field", ""],
    ["a blank field", "   "],
  ])("adds nothing from %s", async (_, text) => {
    // given
    renderField();
    if (text !== "") await user.type(screen.getByRole("combobox"), text);

    // when
    await user.click(screen.getByRole("button", { name: "Add" }));

    // then
    expect(onAdd).not.toHaveBeenCalled();
  });

  it("adds nothing more while an add is in flight", async () => {
    // given
    renderField({ isPending: true });
    await user.type(screen.getByRole("combobox"), "Rust{Enter}");

    // when
    await user.click(screen.getByRole("button", { name: "Add" }));

    // then
    expect(onAdd).not.toHaveBeenCalled();
  });

  it("says why an add was refused", () => {
    // when
    renderField({ errorMessage: "At most 10 areas per community." });

    // then
    expect(
      screen.getByText("At most 10 areas per community."),
    ).toBeInTheDocument();
  });

  it("tells the owner each time the text changes", async () => {
    // given
    const onTextChange = vi.fn();
    renderField({ onTextChange });

    // when
    await user.type(screen.getByRole("combobox"), "ab");

    // then
    expect(onTextChange).toHaveBeenCalledTimes(2);
  });

  it("leads the row with the picker it is given", () => {
    // when
    renderField({ picker: <span>Platform picker</span> });

    // then
    expect(screen.getByText("Platform picker")).toBeInTheDocument();
  });
});
