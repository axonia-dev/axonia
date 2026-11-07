import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";

import { SessionFilter } from "@/features/knowledge/ui/SessionFilter";

vi.mock("@/features/knowledge/api/use-library-insights", () => ({
  useArtifactSessions: vi.fn(),
}));
vi.mock("@/features/events/api/use-sessions", () => ({
  useSessions: vi.fn(),
}));

import { useSessions } from "@/features/events/api/use-sessions";
import { useArtifactSessions } from "@/features/knowledge/api/use-library-insights";

const mockArtifactSessions = vi.mocked(useArtifactSessions);
const mockSessions = vi.mocked(useSessions);

const seriesSession = {
  id: 9,
  seriesId: 2,
  displayTitle: "Backend Dojo",
  occurrenceDate: "2026-06-06",
  scheduledAt: "2026-06-06T10:00:00Z",
  sections: [],
};

const oneOffSession = {
  id: 5,
  seriesId: null,
  displayTitle: "Lightning talks",
  occurrenceDate: "2026-06-10",
  scheduledAt: "2026-06-10T10:00:00Z",
  sections: [{ slots: [{ title: "Lightning talks", presenterUserIds: [] }] }],
};

let user: ReturnType<typeof userEvent.setup>;

beforeEach(() => {
  user = userEvent.setup({ delay: null });
  vi.clearAllMocks();
  mockArtifactSessions.mockReturnValue({
    data: [
      { sessionId: 9, count: 4 },
      { sessionId: 5, count: 2 },
      { sessionId: 99, count: 1 },
    ],
  } as never);
  mockSessions.mockReturnValue({
    data: [seriesSession, oneOffSession],
  } as never);
});

async function openOptions() {
  await user.click(screen.getByRole("button", { name: /session/i }));
}

describe("SessionFilter", () => {
  it("offers the Any session and No session options", async () => {
    // given
    render(<SessionFilter slug="eng" value={-1} onChange={vi.fn()} />);

    // when
    await openOptions();

    // then
    expect(
      screen.getByRole("option", { name: "Any session" }),
    ).toBeInTheDocument();
    expect(
      screen.getByRole("option", { name: "No session" }),
    ).toBeInTheDocument();
  });

  it("labels a session with the name the server gives it", async () => {
    // given
    render(<SessionFilter slug="eng" value={-1} onChange={vi.fn()} />);

    // when
    await openOptions();

    // then
    expect(
      screen.getByRole("option", { name: /Backend Dojo ·/ }),
    ).toBeInTheDocument();
  });

  it("labels a one-off by its name too", async () => {
    // given
    render(<SessionFilter slug="eng" value={-1} onChange={vi.fn()} />);

    // when
    await openOptions();

    // then
    expect(
      screen.getByRole("option", { name: /Lightning talks ·/ }),
    ).toBeInTheDocument();
  });

  it("falls back to a generic label when the session isn't found", async () => {
    // given
    render(<SessionFilter slug="eng" value={-1} onChange={vi.fn()} />);

    // when
    await openOptions();

    // then
    expect(
      screen.getByRole("option", { name: "Session #99" }),
    ).toBeInTheDocument();
  });

  it("lists the newest session first and a session it cannot date last", async () => {
    // given
    render(<SessionFilter slug="eng" value={-1} onChange={vi.fn()} />);

    // when
    await openOptions();

    // then
    expect(
      screen.getAllByRole("option").map((option) => option.textContent),
    ).toEqual([
      "Any session",
      "No session",
      expect.stringMatching(/^Lightning talks ·/),
      expect.stringMatching(/^Backend Dojo ·/),
      "Session #99",
    ]);
  });

  it("reports the chosen session id", async () => {
    // given
    const onChange = vi.fn();
    render(<SessionFilter slug="eng" value={-1} onChange={onChange} />);

    // when
    await openOptions();
    await user.click(screen.getByRole("option", { name: "No session" }));

    // then
    expect(onChange).toHaveBeenCalledWith(0);
  });
});
