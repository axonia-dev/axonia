import { render, screen } from "@testing-library/react";
import { MemoryRouter, Route, Routes } from "react-router-dom";
import { beforeEach, describe, expect, it, vi } from "vitest";

import { stubClipboard } from "@/test/clipboard";

import { DocPageView } from "./DocPageView";

vi.mock("@/features/agent/api/use-personal-access-tokens", () => ({
  useAgentAccess: () => ({ available: true, isLoading: false, resolved: true }),
}));

beforeEach(() => {
  stubClipboard({ writeText: vi.fn().mockResolvedValue(undefined) });
});

function renderAt(path: string) {
  return render(
    <MemoryRouter initialEntries={[path]}>
      <Routes>
        <Route path="/docs/:slug" element={<DocPageView />} />
      </Routes>
    </MemoryRouter>,
  );
}

describe("DocPageView", () => {
  it("renders the page for a known slug", () => {
    renderAt("/docs/agent-overview");
    expect(
      screen.getByRole("heading", { name: /agent overview/i, level: 1 }),
    ).toBeInTheDocument();
  });

  it("renders a not-found state for an unknown slug", () => {
    renderAt("/docs/nope");
    expect(screen.getByText(/page not found/i)).toBeInTheDocument();
  });
});
