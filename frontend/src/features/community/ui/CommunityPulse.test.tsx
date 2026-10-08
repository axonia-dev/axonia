// frontend/src/features/community/ui/CommunityPulse.test.tsx
import { render, screen } from "@testing-library/react";
import { MemoryRouter } from "react-router-dom";
import { beforeEach, describe, expect, it, vi } from "vitest";

import { useMembers } from "@/features/community/api/use-membership";
import { CommunityPulse } from "@/features/community/ui/CommunityPulse";
import {
  useLastHeldSession,
  useSessionsHeldSince,
} from "@/features/events/api/use-sessions";
import { useTopics } from "@/features/events/api/use-topics";
import { useArtifacts } from "@/features/knowledge/api/use-artifacts";

vi.mock("@/features/community/api/use-membership");
vi.mock("@/features/events/api/use-sessions");
vi.mock("@/features/events/api/use-topics");
vi.mock("@/features/knowledge/api/use-artifacts");

const ok = (totalElements: number) => ({
  data: { totalElements, content: [] },
  isPending: false,
  isError: false,
});

beforeEach(() => {
  vi.mocked(useTopics).mockReturnValue(ok(2) as never);
  vi.mocked(useArtifacts).mockReturnValue(ok(5) as never);
  vi.mocked(useMembers).mockReturnValue({
    data: { content: [], totalElements: 3 },
    isPending: false,
    isError: false,
  } as never);
  vi.mocked(useLastHeldSession).mockReturnValue({
    data: null,
    isPending: false,
    isError: false,
  } as never);
  vi.mocked(useSessionsHeldSince).mockReturnValue({
    data: 2,
    isPending: false,
    isError: false,
  } as never);
});

describe("CommunityPulse", () => {
  it("links the Library vital to the library page", () => {
    render(
      <MemoryRouter>
        <CommunityPulse slug="guild" memberCount={3} />
      </MemoryRouter>,
    );
    const link = screen.getByRole("link", { name: /in the library/i });
    expect(link).toHaveAttribute("href", "/communities/guild/library");
  });

  it("degrades a failing vital to a dash without blanking the rail", () => {
    vi.mocked(useSessionsHeldSince).mockReturnValue({
      data: undefined,
      isPending: false,
      isError: true,
    } as never);

    render(
      <MemoryRouter>
        <CommunityPulse slug="guild" memberCount={3} />
      </MemoryRouter>,
    );

    const degraded = screen.getByRole("link", {
      name: /sessions this quarter/i,
    });
    expect(degraded).toHaveTextContent("-");
    // A healthy vital still renders alongside the degraded one.
    expect(
      screen.getByRole("link", { name: /in the library/i }),
    ).toBeInTheDocument();
  });

  it("shows a warm sentinel when no session was ever held", () => {
    render(
      <MemoryRouter>
        <CommunityPulse slug="guild" memberCount={3} />
      </MemoryRouter>,
    );

    expect(screen.getByText(/no sessions held yet/i)).toBeInTheDocument();
  });

  it("counts a last session's single topic in the singular", () => {
    vi.mocked(useLastHeldSession).mockReturnValue({
      data: {
        scheduledAt: "2020-01-01T10:00:00Z",
        sections: [{ slots: [{ presenterUserIds: ["u1"] }] }],
      },
      isPending: false,
      isError: false,
    } as never);

    render(
      <MemoryRouter>
        <CommunityPulse slug="guild" memberCount={3} />
      </MemoryRouter>,
    );

    expect(screen.getByText(/^1 topic ·/)).toBeInTheDocument();
  });

  it("states a quiet month plainly rather than as a green +0", () => {
    vi.mocked(useArtifacts).mockReturnValue(ok(0) as never);

    render(
      <MemoryRouter>
        <CommunityPulse slug="guild" memberCount={3} />
      </MemoryRouter>,
    );

    expect(screen.queryByText(/\+0/)).not.toBeInTheDocument();
    expect(screen.getByText("None joined this month")).toBeInTheDocument();
    expect(screen.getByText("None added this month")).toBeInTheDocument();
  });

  it("draws no dash figure when no session was ever held", () => {
    render(
      <MemoryRouter>
        <CommunityPulse slug="guild" memberCount={3} />
      </MemoryRouter>,
    );

    const vital = screen.getByRole("link", { name: /no sessions held yet/i });
    expect(vital).not.toHaveTextContent("-");
    expect(vital).toHaveAttribute("href", "/communities/guild/schedule");
  });

  it("names each vital in the singular when its count is one", () => {
    vi.mocked(useTopics).mockReturnValue(ok(1) as never);
    vi.mocked(useSessionsHeldSince).mockReturnValue({
      data: 1,
      isPending: false,
      isError: false,
    } as never);
    vi.mocked(useLastHeldSession).mockReturnValue({
      data: {
        scheduledAt: "2020-01-01T10:00:00Z",
        sections: [{ slots: [{ presenterUserIds: ["u1"] }] }],
      },
      isPending: false,
      isError: false,
    } as never);

    render(
      <MemoryRouter>
        <CommunityPulse slug="guild" memberCount={1} />
      </MemoryRouter>,
    );

    expect(screen.getByText("member")).toBeInTheDocument();
    expect(screen.getByText("presenter last session")).toBeInTheDocument();
    expect(screen.getByText("session this quarter")).toBeInTheDocument();
    expect(
      screen.getByText("topic waiting to be scheduled"),
    ).toBeInTheDocument();
  });
});
