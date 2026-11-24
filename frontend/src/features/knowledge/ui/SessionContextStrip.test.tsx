import { render, screen } from "@testing-library/react";
import { MemoryRouter } from "react-router-dom";
import { beforeEach, describe, expect, it, vi } from "vitest";

import { SessionContextStrip } from "@/features/knowledge/ui/SessionContextStrip";

vi.mock("@/features/events/api/use-session", () => ({
  useSession: vi.fn(),
}));
vi.mock("@/features/identity/api/use-users", () => ({
  useUsers: vi.fn(),
}));

import { useSession } from "@/features/events/api/use-session";
import { useUsers } from "@/features/identity/api/use-users";

const mockSession = vi.mocked(useSession);

const session = {
  id: 9,
  seriesId: 2,
  displayTitle: "Backend Dojo",
  occurrenceDate: "2026-06-06",
  scheduledAt: "2026-06-06T10:00:00Z",
  sections: [
    { slots: [{ title: "Talk", presenterUserIds: ["alice", "bob"] }] },
  ],
};

function renderStrip() {
  return render(
    <MemoryRouter>
      <SessionContextStrip slug="eng" sessionId={9} />
    </MemoryRouter>,
  );
}

beforeEach(() => {
  vi.clearAllMocks();
  vi.mocked(useUsers).mockReturnValue({
    data: {
      alice: { id: "alice", displayName: "Alice Anderson" },
      bob: { id: "bob", displayName: "Bob Brown" },
    },
  } as never);
});

describe("SessionContextStrip", () => {
  it("shows the session's name, presenters, and a link to the session", () => {
    // given
    mockSession.mockReturnValue({ data: session } as never);

    // when
    renderStrip();

    // then
    expect(
      screen.getByRole("heading", { name: "Backend Dojo" }),
    ).toBeInTheDocument();
    expect(
      screen.getByText("Sat, Jun 6, 2026 · Alice Anderson, Bob Brown"),
    ).toBeInTheDocument();
    expect(screen.getByRole("link", { name: /open session/i })).toHaveAttribute(
      "href",
      "/communities/eng/sessions/9",
    );
  });

  it("never shows a presenter by their id while their name is unknown", () => {
    // given
    mockSession.mockReturnValue({ data: session } as never);
    vi.mocked(useUsers).mockReturnValue({ data: undefined } as never);

    // when
    renderStrip();

    // then
    expect(screen.getByText("Sat, Jun 6, 2026")).toBeInTheDocument();
    expect(screen.queryByText(/alice/)).toBeNull();
  });

  it("renders nothing until the session resolves", () => {
    // given
    mockSession.mockReturnValue({ data: undefined } as never);

    // when
    renderStrip();

    // then
    expect(screen.queryByRole("link")).toBeNull();
  });
});
