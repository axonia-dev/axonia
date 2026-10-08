import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";

import { stubClipboard } from "@/test/clipboard";

import { OAuthCommand } from "./OAuthCommand";

const writeText = vi.fn(() => Promise.resolve());

let user: ReturnType<typeof userEvent.setup>;

beforeEach(() => {
  // user-event installs its own clipboard, so it is set up before the stub replaces it.
  user = userEvent.setup({ delay: null });
  writeText.mockClear();
  stubClipboard({ writeText });
});

describe("OAuthCommand", () => {
  it("shows the Claude Code command for the live endpoint, with no key to paste", () => {
    render(<OAuthCommand />);

    const command = screen.getByTestId("oauth-command");
    expect(command).toHaveTextContent(
      "claude mcp add --transport http axonia http://localhost:3000/mcp",
    );
    expect(command).not.toHaveTextContent("Authorization");
  });

  it("copies the command", async () => {
    render(<OAuthCommand />);

    await user.click(screen.getByRole("button", { name: /copy/i }));

    expect(writeText).toHaveBeenCalledWith(
      "claude mcp add --transport http axonia http://localhost:3000/mcp",
    );
    expect(screen.getByRole("button", { name: /copied/i })).toBeInTheDocument();
  });
});
