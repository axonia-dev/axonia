import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";

import { TagInput } from "@/components/ui/TagInput";

let user: ReturnType<typeof userEvent.setup>;

beforeEach(() => {
  user = userEvent.setup({ delay: null });
});

describe("TagInput - locked and suggested tags", () => {
  it("draws a locked tag with no way to remove it", () => {
    render(
      <TagInput
        label="Tags"
        value={["ddd"]}
        onChange={vi.fn()}
        lockedTags={["openid"]}
      />,
    );
    expect(screen.getByText("openid")).toBeInTheDocument();
    expect(
      screen.queryByRole("button", { name: /remove tag openid/i }),
    ).not.toBeInTheDocument();
  });

  // A locked tag is already in what gets submitted, so typing its name must not put a second,
  // removable chip beside the locked one saying the same thing.
  it("takes no tag that repeats a locked one", async () => {
    const onChange = vi.fn();
    render(
      <TagInput
        label="Tags"
        value={[]}
        onChange={onChange}
        lockedTags={["openid"]}
      />,
    );
    const input = screen.getByRole("textbox", { name: /tags/i });
    await user.click(input);
    await user.type(input, "openid{Enter}");
    expect(onChange).not.toHaveBeenCalled();
  });

  it("leaves a locked tag alone when Backspace empties the removable ones", async () => {
    const onChange = vi.fn();
    render(
      <TagInput
        label="Tags"
        value={[]}
        onChange={onChange}
        lockedTags={["openid"]}
      />,
    );
    await user.click(screen.getByRole("textbox", { name: /tags/i }));
    await user.keyboard("{Backspace}");
    expect(onChange).not.toHaveBeenCalled();
  });

  it("offers a suggested tag the value does not name, and adds it when pressed", async () => {
    const onChange = vi.fn();
    render(
      <TagInput
        label="Tags"
        value={["ddd"]}
        onChange={onChange}
        suggestedTags={["kafka"]}
      />,
    );
    await user.click(screen.getByRole("button", { name: "Add kafka" }));
    expect(onChange).toHaveBeenCalledWith(["ddd", "kafka"]);
  });

  it("stops offering a suggested tag the value already names", () => {
    render(
      <TagInput
        label="Tags"
        value={["kafka"]}
        onChange={vi.fn()}
        suggestedTags={["kafka"]}
      />,
    );
    expect(
      screen.queryByRole("button", { name: "Add kafka" }),
    ).not.toBeInTheDocument();
  });

  it("stops offering a suggested tag that is locked", () => {
    render(
      <TagInput
        label="Tags"
        value={[]}
        onChange={vi.fn()}
        lockedTags={["openid"]}
        suggestedTags={["openid"]}
      />,
    );
    expect(
      screen.queryByRole("button", { name: "Add openid" }),
    ).not.toBeInTheDocument();
  });
});
