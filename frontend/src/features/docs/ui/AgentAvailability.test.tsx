import { render } from "@testing-library/react";
import { MemoryRouter } from "react-router-dom";
import { describe, expect, it, vi } from "vitest";

import {
  AGENT_ACCESS,
  AGENT_ACCESS_LOADING,
  AGENT_ACCESS_UNKNOWN,
  NO_AGENT_ACCESS,
} from "@/test/agent-access";

import { AgentAvailability } from "./AgentAvailability";

const useAgentAccess = vi.hoisted(() => vi.fn());
vi.mock("@/features/agent/api/use-personal-access-tokens", () => ({
  useAgentAccess,
}));

function renderNotice() {
  return render(
    <MemoryRouter>
      <AgentAvailability />
    </MemoryRouter>,
  );
}

describe("AgentAvailability", () => {
  it("says nothing where agents may connect", () => {
    useAgentAccess.mockReturnValue(AGENT_ACCESS);

    const { container } = renderNotice();

    expect(container).toBeEmptyDOMElement();
  });

  it("says nothing while the answer is still loading, rather than flashing a refusal", () => {
    useAgentAccess.mockReturnValue(AGENT_ACCESS_LOADING);

    const { container } = renderNotice();

    expect(container).toBeEmptyDOMElement();
  });

  it("says nothing when the query errored, since an unanswered question is not a refusal", () => {
    // A network blip errors here for a member whose organization may connect agents too, and
    // that state is unknown rather than refused: promising such a reader a capability they
    // already have would be nonsense. The settings nav and the API keys route guard still fail
    // closed on this same state, which is why the notice reads `resolved` rather than the
    // negation of `isLoading`.
    useAgentAccess.mockReturnValue(AGENT_ACCESS_UNKNOWN);

    const { container } = renderNotice();

    expect(container).toBeEmptyDOMElement();
  });

  it("says nothing on a refusal where the edition names no remedy", () => {
    useAgentAccess.mockReturnValue(NO_AGENT_ACCESS);

    const { container } = renderNotice();

    expect(container).toBeEmptyDOMElement();
  });
});
