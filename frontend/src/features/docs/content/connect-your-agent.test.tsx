import { render, screen } from "@testing-library/react";
import { MemoryRouter } from "react-router-dom";
import { beforeEach, describe, expect, it, vi } from "vitest";

import { stubClipboard } from "@/test/clipboard";

import ConnectYourAgent from "./connect-your-agent.mdx";

vi.mock("@/features/agent/api/use-personal-access-tokens", () => ({
  useAgentAccess: () => ({ available: true, isLoading: false, resolved: true }),
}));

beforeEach(() => {
  stubClipboard({ writeText: vi.fn().mockResolvedValue(undefined) });
});

describe("connect-your-agent content", () => {
  it("renders the guide with the embedded ConfigBox", () => {
    // The router is here for the first step's gated link, which is a real navigation
    // rather than a full page load out of the documentation surface.
    render(
      <MemoryRouter>
        <ConnectYourAgent />
      </MemoryRouter>,
    );
    expect(
      screen.getByRole("heading", { name: /connect an agent/i, level: 1 }),
    ).toBeInTheDocument();
    expect(
      screen.getByRole("tab", { name: "Claude Code" }),
    ).toBeInTheDocument();
  });

  it("gives the OAuth command in the OAuth section, before the API key section", () => {
    render(
      <MemoryRouter>
        <ConnectYourAgent />
      </MemoryRouter>,
    );
    const command = screen.getByTestId("oauth-command");
    const apiKeyHeading = screen.getByRole("heading", {
      name: "Or use an API key",
    });
    expect(command).toHaveTextContent("claude mcp add --transport http axonia");
    expect(
      command.compareDocumentPosition(apiKeyHeading) &
        Node.DOCUMENT_POSITION_FOLLOWING,
    ).toBeTruthy();
  });
});
