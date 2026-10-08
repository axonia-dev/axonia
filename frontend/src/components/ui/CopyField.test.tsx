import { act, fireEvent, render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

import { CopyField } from "@/components/ui/CopyField";

let user: ReturnType<typeof userEvent.setup>;

beforeEach(() => {
  user = userEvent.setup({ delay: null });
});

afterEach(() => {
  vi.useRealTimers();
  vi.unstubAllGlobals();
});

function stubClipboard() {
  const writeText = vi.fn().mockResolvedValue(undefined);
  vi.stubGlobal("navigator", { ...navigator, clipboard: { writeText } });
  return writeText;
}

function stubFailingClipboard() {
  const writeText = vi.fn().mockRejectedValue(new Error("denied"));
  vi.stubGlobal("navigator", { ...navigator, clipboard: { writeText } });
  return writeText;
}

describe("CopyField", () => {
  it("shows its label and value", () => {
    render(
      <CopyField
        label="ACS URL"
        value="https://idp.example/endpoint"
        copyLabel="Copy ACS URL for Acme"
      />,
    );

    expect(screen.getByText("ACS URL")).toBeInTheDocument();
    expect(
      screen.getByText("https://idp.example/endpoint"),
    ).toBeInTheDocument();
  });

  it("copies the value to the clipboard", async () => {
    const writeText = stubClipboard();
    render(
      <CopyField
        label="ACS URL"
        value="https://idp.example/endpoint"
        copyLabel="Copy ACS URL for Acme"
      />,
    );

    await user.click(
      screen.getByRole("button", { name: "Copy ACS URL for Acme" }),
    );

    expect(writeText).toHaveBeenCalledWith("https://idp.example/endpoint");
  });

  it("confirms the copy to the reader", async () => {
    stubClipboard();
    render(
      <CopyField
        label="ACS URL"
        value="https://idp.example/endpoint"
        copyLabel="Copy ACS URL for Acme"
      />,
    );

    await user.click(
      screen.getByRole("button", { name: "Copy ACS URL for Acme" }),
    );

    expect(await screen.findByText("Copied")).toBeInTheDocument();
  });

  it("shows a failed state when the clipboard write is rejected", async () => {
    stubFailingClipboard();
    render(
      <CopyField
        label="ACS URL"
        value="https://idp.example/endpoint"
        copyLabel="Copy ACS URL for Acme"
      />,
    );

    await user.click(
      screen.getByRole("button", { name: "Copy ACS URL for Acme" }),
    );

    expect(await screen.findByText("Copy failed")).toBeInTheDocument();
    expect(screen.queryByText("Copied")).not.toBeInTheDocument();
  });

  it("announces the failed copy to assistive technology", async () => {
    stubFailingClipboard();
    render(
      <CopyField
        label="ACS URL"
        value="https://idp.example/endpoint"
        copyLabel="Copy ACS URL for Acme"
      />,
    );

    await user.click(
      screen.getByRole("button", { name: "Copy ACS URL for Acme" }),
    );

    expect(await screen.findByText("Copy failed.")).toBeInTheDocument();
  });

  it("returns to Copy so the visible label matches its accessible name again", async () => {
    stubClipboard();
    vi.useFakeTimers();
    render(
      <CopyField
        label="ACS URL"
        value="https://idp.example/endpoint"
        copyLabel="Copy ACS URL for Acme"
      />,
    );

    await act(async () => {
      fireEvent.click(
        screen.getByRole("button", { name: "Copy ACS URL for Acme" }),
      );
      await vi.advanceTimersByTimeAsync(0);
    });
    expect(screen.getByText("Copied")).toBeInTheDocument();

    await act(async () => {
      await vi.advanceTimersByTimeAsync(2000);
    });

    expect(screen.getByText("Copy")).toBeInTheDocument();
    expect(screen.queryByText("Copied")).not.toBeInTheDocument();
  });

  it("renders a hint when one is given", () => {
    render(
      <CopyField
        label="ACS URL"
        value="https://idp.example/endpoint"
        copyLabel="Copy ACS URL for Acme"
        hint="Paste this into your identity provider."
      />,
    );

    expect(
      screen.getByText("Paste this into your identity provider."),
    ).toBeInTheDocument();
  });

  it("renders no hint paragraph when none is given", () => {
    render(
      <CopyField
        label="ACS URL"
        value="https://idp.example/endpoint"
        copyLabel="Copy ACS URL for Acme"
      />,
    );

    expect(screen.queryByText(/Paste this/)).not.toBeInTheDocument();
  });

  it("fails gracefully when the clipboard API is unavailable", async () => {
    vi.stubGlobal("navigator", { ...navigator, clipboard: undefined });
    render(
      <CopyField
        label="ACS URL"
        value="https://idp.example/endpoint"
        copyLabel="Copy ACS URL for Acme"
      />,
    );

    await user.click(
      screen.getByRole("button", { name: "Copy ACS URL for Acme" }),
    );

    expect(await screen.findByText("Copy failed")).toBeInTheDocument();
  });

  // A field nothing is waiting on has to be separable from its neighbours before any label is
  // read, so the mark is the field's own shape, not only a word inside it.
  it("marks a value nothing is waiting on, and leaves the rest unmarked", () => {
    const { container, rerender } = render(
      <CopyField
        label="Service provider metadata"
        value="https://idp.example/descriptor"
        copyLabel="Copy the descriptor"
        optional
      />,
    );

    expect(screen.getByText("Optional")).toBeVisible();
    expect(container.firstElementChild).toHaveClass("border-secondary");

    rerender(
      <CopyField
        label="ACS URL"
        value="https://idp.example/endpoint"
        copyLabel="Copy ACS URL for Acme"
      />,
    );

    expect(screen.queryByText("Optional")).not.toBeInTheDocument();
    expect(container.firstElementChild).not.toHaveClass("border-secondary");
  });

  // An identifier wraps where a person would split it, after a separator, never mid-word: the
  // SSO fields read "example-c / orp" and "endpo / int" when any character could end a line.
  it("lets a long identifier wrap only after its separators", () => {
    // when
    render(
      <CopyField
        label="Entity ID"
        value="https://sso.acme.example/realms/example-corp"
        copyLabel="Copy the entity ID"
      />,
    );

    // then
    const value = screen.getByText(
      "https://sso.acme.example/realms/example-corp",
    );
    expect(value.innerHTML).toBe(
      "https://<wbr>sso.<wbr>acme.<wbr>example/<wbr>realms/<wbr>example-<wbr>corp",
    );
    expect(value).toHaveClass("wrap-anywhere");
  });
});
