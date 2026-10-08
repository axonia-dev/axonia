import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";

import { stubClipboard } from "@/test/clipboard";

import { ShowOnceSecret } from "./ShowOnceSecret";

let user: ReturnType<typeof userEvent.setup>;

beforeEach(() => {
  user = userEvent.setup({ delay: null });
});

describe("ShowOnceSecret", () => {
  it("renders the secret and a once-only note, and copies", async () => {
    const writeText = vi.fn().mockResolvedValue(undefined);
    stubClipboard({ writeText });

    render(
      <ShowOnceSecret
        label="Your new token"
        note="Shown only once."
        secret="axp_abc"
      />,
    );

    expect(screen.getByText("axp_abc")).toBeInTheDocument();
    expect(screen.getByRole("note")).toHaveTextContent("Shown only once.");

    await user.click(screen.getByRole("button", { name: "Copy" }));
    expect(writeText).toHaveBeenCalledWith("axp_abc");
    expect(
      await screen.findByRole("button", { name: "Copied!" }),
    ).toBeInTheDocument();
  });
});
