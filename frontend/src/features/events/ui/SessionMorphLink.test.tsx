import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter, Route, Routes } from "react-router-dom";
import { describe, expect, it, vi } from "vitest";

import { SessionMorphLink } from "@/features/events/ui/SessionMorphLink";

const session = vi.hoisted(() => ({
  ready: vi.fn(() => Promise.resolve()),
  prefetch: vi.fn(),
}));

vi.mock("@/features/events/api/use-ready-session", () => ({
  useReadySession: () => session,
}));

function renderLink(sessionId: number | null) {
  const community = {
    ready: vi.fn(() => Promise.resolve()),
    prefetch: vi.fn(),
  };
  render(
    <MemoryRouter>
      <Routes>
        <Route
          path="/"
          element={
            <SessionMorphLink
              to="/sessions/7"
              sessionId={sessionId}
              alsoReady={community}
            >
              Open
            </SessionMorphLink>
          }
        />
        <Route path="/sessions/7" element={<p>Session page</p>} />
      </Routes>
    </MemoryRouter>,
  );
  return community;
}

describe("SessionMorphLink", () => {
  it("readies the session and the extra read on intent", async () => {
    // given
    const user = userEvent.setup({ delay: null });
    const community = renderLink(7);

    // when
    await user.hover(screen.getByRole("link", { name: "Open" }));
    await user.click(screen.getByRole("link", { name: "Open" }));

    // then
    expect(session.prefetch).toHaveBeenCalledWith(7);
    expect(community.prefetch).toHaveBeenCalled();
    expect(session.ready).toHaveBeenCalledWith(7);
    expect(community.ready).toHaveBeenCalled();
    expect(await screen.findByText("Session page")).toBeInTheDocument();
  });

  it("is a plain link without a session", async () => {
    // given
    const user = userEvent.setup({ delay: null });
    const community = renderLink(null);

    // when
    await user.hover(screen.getByRole("link", { name: "Open" }));
    await user.click(screen.getByRole("link", { name: "Open" }));

    // then
    expect(community.prefetch).not.toHaveBeenCalled();
    expect(await screen.findByText("Session page")).toBeInTheDocument();
  });
});
