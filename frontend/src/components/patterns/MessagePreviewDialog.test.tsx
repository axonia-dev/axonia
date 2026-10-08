import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

import { MessagePreviewDialog } from "@/components/patterns/MessagePreviewDialog";
import * as openMessageInTab from "@/components/patterns/open-message-in-tab";

let user: ReturnType<typeof userEvent.setup>;

beforeEach(() => {
  user = userEvent.setup({ delay: null });
});

function renderDialog(
  preview: Partial<{
    data: { subject: string; html: string };
    isLoading: boolean;
    isError: boolean;
  }>,
) {
  render(
    <MessagePreviewDialog
      isOpen
      onOpenChange={vi.fn()}
      dialogTitle="Wrap-up preview"
      iframeTitle="Wrap-up email preview"
      preview={{
        data: undefined,
        isLoading: false,
        isError: false,
        ...preview,
      }}
      errorMessage="Could not render it. Try again."
    />,
  );
}

describe("MessagePreviewDialog", () => {
  afterEach(() => {
    vi.restoreAllMocks();
  });

  it("announces that the render is in flight, rather than showing an empty dialog", () => {
    // when
    renderDialog({ isLoading: true });

    // then - a status, so a screen reader is told something is coming
    expect(screen.getByRole("status")).toHaveTextContent(/loading preview/i);
  });

  it("surfaces the caller's own failure copy as an alert", () => {
    // when
    renderDialog({ isError: true });

    // then
    expect(screen.getByRole("alert")).toHaveTextContent(/could not render it/i);
  });

  it("renders the subject and the HTML inside a sandboxed, titled iframe", () => {
    // when
    renderDialog({
      data: { subject: "Platform Guild wrapped up", html: "<p>Thanks</p>" },
    });

    // then
    expect(screen.getByText("Platform Guild wrapped up")).toBeInTheDocument();
    const iframe = screen.getByTitle("Wrap-up email preview");
    expect(iframe.tagName).toBe("IFRAME");
    expect(iframe).toHaveAttribute("srcdoc", "<p>Thanks</p>");
    // An empty `sandbox` attribute is the strictest setting - present, but
    // granting no permissions back to the rendered mail HTML.
    expect(iframe).toHaveAttribute("sandbox", "");
  });

  it("shows nothing of the message while it is closed", () => {
    // given
    render(
      <MessagePreviewDialog
        isOpen={false}
        onOpenChange={vi.fn()}
        dialogTitle="Wrap-up preview"
        iframeTitle="Wrap-up email preview"
        preview={{
          data: { subject: "Platform Guild wrapped up", html: "<p>x</p>" },
          isLoading: false,
          isError: false,
        }}
        errorMessage="Could not render it. Try again."
      />,
    );

    // then
    expect(
      screen.queryByTitle("Wrap-up email preview"),
    ).not.toBeInTheDocument();
  });

  it("offers desktop and phone widths, starting on desktop", () => {
    // when
    renderDialog({
      data: { subject: "Platform Guild wrapped up", html: "<p>Thanks</p>" },
    });

    // then
    expect(screen.getByRole("radio", { name: "Desktop" })).toHaveAttribute(
      "aria-checked",
      "true",
    );
    expect(screen.getByRole("radio", { name: "Phone" })).toHaveAttribute(
      "aria-checked",
      "false",
    );
  });

  it("narrows the frame to a phone width without re-rendering the message", async () => {
    // given
    renderDialog({
      data: { subject: "Platform Guild wrapped up", html: "<p>Thanks</p>" },
    });
    const before = screen.getByTitle("Wrap-up email preview");
    expect(before.parentElement?.className).toContain("max-w-[680px]");

    // when
    await user.click(screen.getByRole("radio", { name: "Phone" }));

    // then - same DOM node, so the mail is not re-parsed
    const after = screen.getByTitle("Wrap-up email preview");
    expect(after).toBe(before);
    expect(after.parentElement?.className).toContain("max-w-[400px]");
  });

  it("offers no width toggle while there is no message to size", () => {
    // when
    renderDialog({ isLoading: true });

    // then
    expect(screen.queryByRole("radio", { name: "Phone" })).toBeNull();
  });

  it("hides the width toggle below the sm breakpoint", () => {
    // when
    renderDialog({
      data: { subject: "Platform Guild wrapped up", html: "<p>Thanks</p>" },
    });

    // then - jsdom does not evaluate media queries, so there is no way to assert
    // that the control is actually invisible at a phone viewport; this asserts
    // the `hidden sm:flex` class that produces that effect instead.
    const group = screen.getByRole("radiogroup", { name: "Preview width" });
    expect(group.parentElement?.className).toContain("hidden");
    expect(group.parentElement?.className).toContain("sm:flex");
  });

  it("opens the message in a new tab via the shared wrapper, not the caller", async () => {
    // given
    const openSpy = vi
      .spyOn(openMessageInTab, "openMessageInNewTab")
      .mockImplementation(() => {});
    renderDialog({
      data: { subject: "Platform Guild wrapped up", html: "<p>Thanks</p>" },
    });

    // when
    await user.click(screen.getByRole("button", { name: "Open in new tab" }));

    // then
    expect(openSpy).toHaveBeenCalledWith(
      "Platform Guild wrapped up",
      "<p>Thanks</p>",
    );
  });
});
