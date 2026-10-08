import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";

import { stubClipboard } from "@/test/clipboard";

import { ConfigBox } from "./ConfigBox";

const writeText = vi.fn(() => Promise.resolve());

let user: ReturnType<typeof userEvent.setup>;

beforeEach(() => {
  user = userEvent.setup({ delay: null });
  writeText.mockClear();
  stubClipboard({ writeText });
});

describe("ConfigBox", () => {
  it("shows the Claude Code snippet with the live endpoint and the key by default", () => {
    render(<ConfigBox />);
    const code = screen.getByTestId("config-snippet");
    expect(code).toHaveTextContent("claude mcp add");
    expect(code).toHaveTextContent("http://localhost:3000/mcp");
    expect(code).toHaveTextContent('--header "Authorization: Bearer axp_');
  });

  it("switches the snippet when another provider tab is selected", async () => {
    render(<ConfigBox />);
    await user.click(screen.getByRole("tab", { name: "Cursor" }));
    expect(screen.getByTestId("config-snippet")).toHaveTextContent(
      ".cursor/mcp.json",
    );
  });

  it("copies the current snippet to the clipboard", async () => {
    render(<ConfigBox />);
    await user.click(screen.getByRole("button", { name: /copy/i }));
    expect(writeText).toHaveBeenCalledWith(
      expect.stringContaining("http://localhost:3000/mcp"),
    );
    expect(screen.getByRole("button", { name: /copied/i })).toBeInTheDocument();
  });
});
