import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";

import { Button } from "@/components/ui/Button";
import { Dialog } from "@/components/ui/Dialog";

let user: ReturnType<typeof userEvent.setup>;

beforeEach(() => {
  user = userEvent.setup({ delay: null });
});

describe("Dialog", () => {
  it("renders the title and content while open", () => {
    // when
    render(
      <Dialog isOpen onOpenChange={vi.fn()} title="Confirm">
        <p>Body copy</p>
      </Dialog>,
    );

    // then
    expect(screen.getByRole("dialog")).toBeInTheDocument();
    expect(screen.getByText("Confirm")).toBeInTheDocument();
    expect(screen.getByText("Body copy")).toBeInTheDocument();
  });

  it("renders nothing while closed", () => {
    // when
    render(
      <Dialog isOpen={false} onOpenChange={vi.fn()} title="Confirm">
        <p>Body copy</p>
      </Dialog>,
    );

    // then
    expect(screen.queryByRole("dialog")).toBeNull();
    expect(screen.queryByText("Body copy")).toBeNull();
  });

  it("can present as an alertdialog for destructive confirmations", () => {
    // when
    render(
      <Dialog isOpen onOpenChange={vi.fn()} title="Danger" role="alertdialog">
        <p>Body copy</p>
      </Dialog>,
    );

    // then
    expect(screen.getByRole("alertdialog")).toBeInTheDocument();
  });

  it("asks to close when Escape is pressed", async () => {
    // given
    const onOpenChange = vi.fn();

    // when
    render(
      <Dialog isOpen onOpenChange={onOpenChange} title="Confirm">
        <p>Body copy</p>
      </Dialog>,
    );
    await user.keyboard("{Escape}");

    // then
    expect(onOpenChange).toHaveBeenCalledWith(false);
  });

  it("hands a close callback to a render-prop child", async () => {
    // given
    const onOpenChange = vi.fn();

    // when
    render(
      <Dialog isOpen onOpenChange={onOpenChange} title="Confirm">
        {({ close }) => <Button onPress={close}>Done</Button>}
      </Dialog>,
    );
    await user.click(screen.getByRole("button", { name: "Done" }));

    // then
    expect(onOpenChange).toHaveBeenCalledWith(false);
  });

  it("bottom-sheets on mobile and centers from sm up", () => {
    render(
      <Dialog isOpen onOpenChange={vi.fn()} title="Confirm">
        <p>Body copy</p>
      </Dialog>,
    );

    const panel = screen.getByRole("dialog");
    expect(panel.className).toContain("rounded-t-lg");
    expect(panel.className).toContain("sm:rounded-lg");
  });

  it("defaults to the md width when no size is given", () => {
    render(
      <Dialog isOpen onOpenChange={vi.fn()} title="Confirm">
        <p>Body copy</p>
      </Dialog>,
    );

    const modal = screen.getByRole("dialog").parentElement;
    expect(modal?.className).toContain("sm:max-w-md");
    expect(modal?.className).not.toContain("sm:max-w-2xl");
  });

  it("widens to the 2xl size for content with its own intrinsic width", () => {
    render(
      <Dialog isOpen onOpenChange={vi.fn()} title="Confirm" size="2xl">
        <p>Body copy</p>
      </Dialog>,
    );

    const modal = screen.getByRole("dialog").parentElement;
    expect(modal?.className).toContain("sm:max-w-2xl");
    expect(modal?.className).not.toContain("sm:max-w-md");
  });

  it("fills the viewport for a takeover, so a rendered email is not read through a porthole", () => {
    render(
      <Dialog isOpen onOpenChange={vi.fn()} title="Preview" size="takeover">
        <p>Body copy</p>
      </Dialog>,
    );

    const panel = screen.getByRole("dialog");
    expect(panel.parentElement?.className).toContain("sm:max-w-[68rem]");
    expect(panel.className).toContain("h-[92vh]");
    expect(panel.className).not.toContain("max-h-[85vh]");
  });

  it("keeps an ordinary dialog at its content height", () => {
    render(
      <Dialog isOpen onOpenChange={vi.fn()} title="Confirm">
        <p>Body copy</p>
      </Dialog>,
    );

    const panel = screen.getByRole("dialog");
    expect(panel.className).toContain("max-h-[85vh]");
    expect(panel.className).not.toContain("h-[92vh]");
  });
});
