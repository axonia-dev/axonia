import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";

import { IconPicker } from "@/components/ui/IconPicker";

let user: ReturnType<typeof userEvent.setup>;

beforeEach(() => {
  user = userEvent.setup({ delay: null });
});

describe("IconPicker", () => {
  it("selects a curated quick pick", async () => {
    // given
    const onChange = vi.fn();
    render(<IconPicker value={null} onChange={onChange} />);

    // when
    const fileText = await screen.findByRole("button", { name: "file-text" });
    await user.click(fileText);

    // then
    expect(onChange).toHaveBeenCalledWith("file-text");
  });

  it("filters the full library by substring and selects a match", async () => {
    // given
    const onChange = vi.fn();
    render(<IconPicker value={null} onChange={onChange} />);

    // when
    await user.type(screen.getByRole("textbox"), "git-branch");
    const match = await screen.findByRole("button", { name: "git-branch" });
    await user.click(match);

    // then
    expect(onChange).toHaveBeenCalledWith("git-branch");
  });

  it("reports when no icons match the query", async () => {
    // given
    render(<IconPicker value={null} onChange={vi.fn()} />);

    // when
    await user.type(screen.getByRole("textbox"), "zzznotanicon");

    // then
    expect(await screen.findByText("No icons match")).toBeInTheDocument();
  });
});
