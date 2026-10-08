import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";

import {
  AccentControl,
  BadgeEditZone,
  BannerEditZone,
} from "@/features/community/ui/look-and-feel-controls";
import { fileTransfer } from "@/test/file-drop";

let user: ReturnType<typeof userEvent.setup>;

beforeEach(() => {
  user = userEvent.setup({ delay: null });
});

/** The DropZone root holds the drop handlers; its accessible label sits on an inner button. */
function dropZone(label: string) {
  const zone = screen.getByLabelText(label).closest(".outline-none");
  if (!zone) {
    throw new Error(`drop zone not found: ${label}`);
  }
  return zone;
}

describe("BannerEditZone", () => {
  it("offers Add when there is no banner", () => {
    // when
    render(
      <BannerEditZone
        hasImage={false}
        busy={false}
        onAdd={vi.fn()}
        onRemove={vi.fn()}
      />,
    );

    // then
    expect(
      screen.getByRole("button", { name: /add banner/i }),
    ).toBeInTheDocument();
  });

  it("offers Remove when a banner is present and calls back", async () => {
    // given
    const onRemove = vi.fn();
    render(
      <BannerEditZone
        hasImage
        busy={false}
        onAdd={vi.fn()}
        onRemove={onRemove}
      />,
    );

    // when
    await user.click(screen.getByRole("button", { name: /remove banner/i }));

    // then
    expect(onRemove).toHaveBeenCalledOnce();
  });

  it("accepts an image file dropped onto the banner", async () => {
    // given
    const onAdd = vi.fn();
    render(
      <BannerEditZone
        hasImage={false}
        busy={false}
        onAdd={onAdd}
        onRemove={vi.fn()}
      />,
    );
    const zone = dropZone("Banner image drop zone");
    const file = new File(["x"], "banner.png", { type: "image/png" });

    // when — drag highlights the zone, then drop hands over the file
    fireEvent.dragEnter(zone, { dataTransfer: fileTransfer(file) });
    expect(zone).toHaveAttribute("data-drop-target");
    fireEvent.drop(zone, { dataTransfer: fileTransfer(file) });

    // then
    await waitFor(() => expect(onAdd).toHaveBeenCalledWith(file));
  });

  it("ignores a dropped file of an unsupported type", () => {
    // given
    const onAdd = vi.fn();
    render(
      <BannerEditZone
        hasImage={false}
        busy={false}
        onAdd={onAdd}
        onRemove={vi.fn()}
      />,
    );
    const zone = dropZone("Banner image drop zone");
    const file = new File(["x"], "notes.pdf", { type: "application/pdf" });

    // when
    fireEvent.drop(zone, { dataTransfer: fileTransfer(file) });

    // then
    expect(onAdd).not.toHaveBeenCalled();
  });

  it("still replaces the banner when a file is dropped on the reposition surface", async () => {
    // given the reposition control sits inside the drop zone, above the drop
    // target, exactly as LookAndFeelEditor composes it (R11: a drop event
    // must bubble from that descendant up to the DropZone)
    const onAdd = vi.fn();
    render(
      <BannerEditZone
        hasImage
        busy={false}
        onAdd={onAdd}
        onRemove={vi.fn()}
        reposition={<div data-testid="repo" className="absolute inset-0" />}
      />,
    );
    const surface = screen.getByTestId("repo");
    const file = new File(["x"], "banner.png", { type: "image/png" });

    // when
    fireEvent.drop(surface, { dataTransfer: fileTransfer(file) });

    // then
    await waitFor(() => expect(onAdd).toHaveBeenCalledWith(file));
  });
});

describe("BadgeEditZone", () => {
  it("offers Add logo when there is no logo", () => {
    // when
    render(
      <BadgeEditZone
        hasImage={false}
        busy={false}
        onAdd={vi.fn()}
        onRemove={vi.fn()}
      />,
    );

    // then
    expect(
      screen.getByRole("button", { name: /add logo/i }),
    ).toBeInTheDocument();
  });

  it("offers Remove logo when a logo is present and calls back", async () => {
    // given
    const onRemove = vi.fn();
    render(
      <BadgeEditZone
        hasImage
        busy={false}
        onAdd={vi.fn()}
        onRemove={onRemove}
      />,
    );

    // when
    await user.click(screen.getByRole("button", { name: /remove logo/i }));

    // then
    expect(onRemove).toHaveBeenCalledOnce();
  });

  it("accepts an image file dropped onto the logo", async () => {
    // given
    const onAdd = vi.fn();
    render(
      <BadgeEditZone
        hasImage={false}
        busy={false}
        onAdd={onAdd}
        onRemove={vi.fn()}
      />,
    );
    const zone = dropZone("Logo image drop zone");
    const file = new File(["x"], "logo.png", { type: "image/png" });

    // when — drag highlights the zone, then drop hands over the file
    fireEvent.dragEnter(zone, { dataTransfer: fileTransfer(file) });
    expect(zone).toHaveAttribute("data-drop-target");
    fireEvent.drop(zone, { dataTransfer: fileTransfer(file) });

    // then
    await waitFor(() => expect(onAdd).toHaveBeenCalledWith(file));
  });

  it("hides the add affordance while a file is dragged over the logo", () => {
    // given
    render(
      <BadgeEditZone
        hasImage={false}
        busy={false}
        onAdd={vi.fn()}
        onRemove={vi.fn()}
      />,
    );
    const zone = dropZone("Logo image drop zone");
    const affordance = screen.getByRole("button", {
      name: /add logo/i,
    }).parentElement;
    const file = new File(["x"], "logo.png", { type: "image/png" });

    // when a drag enters the zone
    fireEvent.dragEnter(zone, { dataTransfer: fileTransfer(file) });

    // then the idle "+" affordance is faded out, leaving only the drop highlight
    expect(affordance).toHaveClass("opacity-0");
  });

  it("replaces the logo when an image is dropped over an existing one", async () => {
    // given a logo is already set
    const onAdd = vi.fn();
    render(
      <BadgeEditZone hasImage busy={false} onAdd={onAdd} onRemove={vi.fn()} />,
    );
    const zone = dropZone("Logo image drop zone");
    const file = new File(["x"], "new-logo.webp", { type: "image/webp" });

    // when
    fireEvent.drop(zone, { dataTransfer: fileTransfer(file) });

    // then
    await waitFor(() => expect(onAdd).toHaveBeenCalledWith(file));
  });

  it("still replaces the logo when a file is dropped on the reposition surface", async () => {
    // given (see the matching banner test: R11 relies on drop bubbling)
    const onAdd = vi.fn();
    render(
      <BadgeEditZone
        hasImage
        busy={false}
        onAdd={onAdd}
        onRemove={vi.fn()}
        reposition={<div data-testid="repo" className="absolute inset-0" />}
      />,
    );
    const surface = screen.getByTestId("repo");
    const file = new File(["x"], "new-logo.webp", { type: "image/webp" });

    // when
    fireEvent.drop(surface, { dataTransfer: fileTransfer(file) });

    // then
    await waitFor(() => expect(onAdd).toHaveBeenCalledWith(file));
  });
});

describe("AccentControl", () => {
  it("applies a swatch and can clear", async () => {
    // given
    const onChange = vi.fn();
    render(<AccentControl value={null} onChange={onChange} />);

    // when — open the popover and pick the first swatch
    await user.click(screen.getByRole("button", { name: /accent/i }));
    await user.click(screen.getByRole("button", { name: /accent #de3b78/i }));

    // then
    expect(onChange).toHaveBeenCalledWith("#de3b78");
  });

  it("marks the active swatch as selected", async () => {
    // given the current accent matches a palette swatch
    render(<AccentControl value="#DE3B78" onChange={vi.fn()} />);

    // when the popover is opened
    await user.click(screen.getByRole("button", { name: /accent/i }));

    // then that swatch carries the selected ring
    expect(screen.getByRole("button", { name: /accent #de3b78/i })).toHaveClass(
      "ring-[var(--foreground)]",
    );
  });
});
