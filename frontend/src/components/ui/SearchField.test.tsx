import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";

import { SearchField } from "@/components/ui/SearchField";

let user: ReturnType<typeof userEvent.setup>;

beforeEach(() => {
  user = userEvent.setup({ delay: null });
});

describe("SearchField", () => {
  it("exposes its label to assistive tech", () => {
    // when
    render(
      <SearchField label="Filter communities" value="" onChange={vi.fn()} />,
    );

    // then
    expect(screen.getByLabelText("Filter communities")).toBeInTheDocument();
  });

  it("calls onChange with the typed value", async () => {
    // given
    const onChange = vi.fn();
    render(
      <SearchField label="Filter communities" value="" onChange={onChange} />,
    );

    // when
    await user.type(screen.getByLabelText("Filter communities"), "g");

    // then
    expect(onChange).toHaveBeenCalledWith("g");
  });

  it("offers a clear control only once there is text to clear", async () => {
    // given
    const onChange = vi.fn();
    const { rerender } = render(
      <SearchField label="Filter communities" value="" onChange={onChange} />,
    );
    expect(
      screen.queryByRole("button", { name: "Clear search" }),
    ).not.toBeInTheDocument();

    // when
    rerender(
      <SearchField
        label="Filter communities"
        value="guild"
        onChange={onChange}
      />,
    );
    await user.click(screen.getByRole("button", { name: "Clear search" }));

    // then
    expect(onChange).toHaveBeenCalledWith("");
    expect(screen.getByLabelText("Filter communities")).toHaveFocus();
  });
});
