import { render, screen } from "@testing-library/react";
import { MemoryRouter } from "react-router-dom";
import { describe, expect, it, vi } from "vitest";

import {
  AGENT_ACCESS,
  AGENT_ACCESS_LOADING,
  NO_AGENT_ACCESS,
} from "@/test/agent-access";

import { AgentGatedLink } from "./AgentGatedLink";

const useAgentAccess = vi.hoisted(() => vi.fn());
vi.mock("@/features/agent/api/use-personal-access-tokens", () => ({
  useAgentAccess,
}));

function renderLink() {
  return render(
    <MemoryRouter>
      <AgentGatedLink to="/me/api-keys">API keys page</AgentGatedLink>
    </MemoryRouter>,
  );
}

describe("AgentGatedLink", () => {
  it("links where agents may connect", () => {
    useAgentAccess.mockReturnValue(AGENT_ACCESS);

    renderLink();

    expect(screen.getByRole("link", { name: "API keys page" })).toHaveAttribute(
      "href",
      "/me/api-keys",
    );
  });

  it("reads as plain text where they may not, rather than sending the reader to a route that ejects them", () => {
    useAgentAccess.mockReturnValue(NO_AGENT_ACCESS);

    renderLink();

    expect(screen.getByText("API keys page")).toBeVisible();
    expect(screen.queryByRole("link")).not.toBeInTheDocument();
  });

  it("stays plain text while the answer is unknown, matching the route guard's own refusal", () => {
    useAgentAccess.mockReturnValue(AGENT_ACCESS_LOADING);

    renderLink();

    expect(screen.queryByRole("link")).not.toBeInTheDocument();
  });
});
