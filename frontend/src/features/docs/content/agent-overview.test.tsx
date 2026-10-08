import { render, screen } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";

import AgentOverview from "./agent-overview.mdx";

vi.mock("@/features/agent/api/use-personal-access-tokens", () => ({
  useAgentAccess: () => ({ available: true, isLoading: false, resolved: true }),
}));

describe("agent-overview content", () => {
  it("renders its heading", () => {
    render(<AgentOverview />);
    expect(
      screen.getByRole("heading", { name: /agent overview/i, level: 1 }),
    ).toBeInTheDocument();
  });
});
