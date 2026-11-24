import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";

import { SessionFilter } from "@/features/knowledge/ui/SessionFilter";

vi.mock("@/features/knowledge/api/use-library-insights", () => ({
  useArtifactSessions: vi.fn(),
}));
import { useArtifactSessions } from "@/features/knowledge/api/use-library-insights";

const mockArtifactSessions = vi.mocked(useArtifactSessions);

let user: ReturnType<typeof userEvent.setup>;

beforeEach(() => {
  user = userEvent.setup({ delay: null });
  vi.clearAllMocks();
  mockArtifactSessions.mockReturnValue({
    data: [
      {
        sessionId: 5,
        count: 2,
        name: "Lightning talks",
        heldOn: "2026-06-10",
      },
      { sessionId: 9, count: 4, name: "Backend Dojo", heldOn: "2026-06-06" },
      { sessionId: 99, count: 1, name: null, heldOn: null },
    ],
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

  it("labels a session with the name and day the server gives it", async () => {
    // given
    render(<SessionFilter slug="eng" value={-1} onChange={vi.fn()} />);

    // when
    await openOptions();

    // then
    expect(
      screen.getByRole("option", { name: "Backend Dojo · Jun 6, 2026" }),
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

  it("lists the sessions in the order the server gives them", async () => {
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
