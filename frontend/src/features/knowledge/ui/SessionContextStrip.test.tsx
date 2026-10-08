import { render, screen } from "@testing-library/react";
import { MemoryRouter } from "react-router-dom";
import { beforeEach, describe, expect, it, vi } from "vitest";

import { SessionContextStrip } from "@/features/knowledge/ui/SessionContextStrip";

vi.mock("@/features/events/api/use-session", () => ({
  useSession: vi.fn(),
}));
vi.mock("@/features/events/api/use-series", () => ({
  useSeriesList: vi.fn(),
}));

import { useSeriesList } from "@/features/events/api/use-series";
import { useSession } from "@/features/events/api/use-session";

const mockSession = vi.mocked(useSession);
const mockSeries = vi.mocked(useSeriesList);

const session = {
  id: 9,
  seriesId: 2,
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
  mockSeries.mockReturnValue({
    data: { content: [{ id: 2, title: "Backend Dojo" }] },
  } as never);
});

describe("SessionContextStrip", () => {
  it("shows the series title, presenters, and a link to the session", () => {
    // given
    mockSession.mockReturnValue({ data: session } as never);

    // when
    renderStrip();

    // then
    expect(
      screen.getByRole("heading", { name: "Backend Dojo" }),
    ).toBeInTheDocument();
    expect(screen.getByText(/alice, bob/)).toBeInTheDocument();
    expect(screen.getByRole("link", { name: /open session/i })).toHaveAttribute(
      "href",
      "/communities/eng/sessions/9",
    );
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
