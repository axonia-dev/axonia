import { fireEvent, render, screen } from "@testing-library/react";
import { MemoryRouter } from "react-router-dom";
import { beforeEach, describe, expect, it, vi } from "vitest";

import { useSettledActionItems } from "@/features/events/api/use-action-items";
import { usePastTopics } from "@/features/events/api/use-topics";
import { PastTopicsPanel } from "@/features/events/ui/PastTopicsPanel";
import { anActionItem } from "@/test/action-item-fixture";

vi.mock("@/features/events/api/use-topics", () => ({
  usePastTopics: vi.fn(),
}));
vi.mock("@/features/events/api/use-action-items", () => ({
  useSettledActionItems: vi.fn(),
}));

const mockUsePastTopics = vi.mocked(usePastTopics);
const mockUseSettled = vi.mocked(useSettledActionItems);

beforeEach(() => {
  vi.clearAllMocks();
  mockUseSettled.mockReturnValue({
    items: [],
    total: 0,
    isLoading: false,
    isError: false,
  });
});

function pastTopic(id: number, title: string) {
  return {
    id,
    title,
    kind: "REQUEST",
    status: "DONE",
    voteCount: 0,
    updatedAt: "2001-01-01T18:00:00Z",
    scheduledFor: {
      sessionId: id,
      scheduledAt: "2001-01-01T18:00:00Z",
      scheduleLabel: "Frontend Dojo",
    },
  };
}

describe("PastTopicsPanel", () => {
  it("renders a past topic's title and session label", () => {
    mockUsePastTopics.mockReturnValue({
      data: { content: [pastTopic(1, "Kafka retro")], totalPages: 1 },
      isLoading: false,
      isError: false,
    } as never);
    render(
      <MemoryRouter>
        <PastTopicsPanel slug="backend-guild" />
      </MemoryRouter>,
    );
    expect(screen.getByText("Kafka retro")).toBeInTheDocument();
    expect(screen.getByText(/Frontend Dojo/)).toBeInTheDocument();
  });

  it("shows no reopen control on a past topic", () => {
    mockUsePastTopics.mockReturnValue({
      data: {
        content: [
          { ...pastTopic(3, "An Action"), scheduledFor: null, canReopen: true },
        ],
        totalPages: 1,
      },
      isLoading: false,
      isError: false,
    } as never);
    render(
      <MemoryRouter>
        <PastTopicsPanel slug="backend-guild" />
      </MemoryRouter>,
    );
    expect(
      screen.queryByRole("button", { name: /back to the backlog/i }),
    ).not.toBeInTheDocument();
  });

  it("searches and resets to the first page", () => {
    mockUsePastTopics.mockReturnValue({
      data: { content: [], totalPages: 0 },
      isLoading: false,
      isError: false,
    } as never);
    render(
      <MemoryRouter>
        <PastTopicsPanel slug="backend-guild" />
      </MemoryRouter>,
    );

    fireEvent.change(screen.getByRole("searchbox"), {
      target: { value: "kafka" },
    });

    const lastCall = mockUsePastTopics.mock.calls.at(-1);
    expect(lastCall?.[1]).toMatchObject({ page: 0, q: "kafka" });
  });

  it("shows pagination only when there is more than one page", () => {
    mockUsePastTopics.mockReturnValue({
      data: { content: [pastTopic(1, "A")], totalPages: 3 },
      isLoading: false,
      isError: false,
    } as never);
    render(
      <MemoryRouter>
        <PastTopicsPanel slug="backend-guild" />
      </MemoryRouter>,
    );
    expect(
      screen.getByRole("navigation", { name: /pagination/i }),
    ).toBeInTheDocument();
  });

  it("shows a date tile from the session date, kind badge, session label, and votes", () => {
    mockUsePastTopics.mockReturnValue({
      data: {
        content: [
          {
            id: 1,
            title: "Feature flags retro",
            kind: "OFFER",
            status: "DONE",
            voteCount: 14,
            canReopen: false,
            updatedAt: "2026-07-20T12:00:00Z",
            scheduledFor: {
              sessionId: 9,
              scheduledAt: "2026-07-09T18:00:00Z",
              scheduleLabel: "Frontend Dojo",
            },
          },
        ],
        totalPages: 1,
      },
      isLoading: false,
      isError: false,
    } as never);

    render(
      <MemoryRouter>
        <PastTopicsPanel slug="backend-guild" />
      </MemoryRouter>,
    );

    expect(screen.getByText("9")).toBeInTheDocument();
    expect(screen.getByText("Jul")).toBeInTheDocument();
    expect(screen.getByText("Offer")).toBeInTheDocument();
    expect(screen.getByRole("link", { name: /Frontend Dojo/ })).toHaveAttribute(
      "href",
      "/communities/backend-guild/sessions/9",
    );
    expect(screen.getByText(/14 votes when held/)).toBeInTheDocument();
  });

  it("uses singular copy for a single vote", () => {
    mockUsePastTopics.mockReturnValue({
      data: {
        content: [{ ...pastTopic(4, "Solo vote talk"), voteCount: 1 }],
        totalPages: 1,
      },
      isLoading: false,
      isError: false,
    } as never);

    render(
      <MemoryRouter>
        <PastTopicsPanel slug="backend-guild" />
      </MemoryRouter>,
    );

    expect(screen.getByText(/1 vote when held/)).toBeInTheDocument();
    expect(screen.queryByText(/1 votes/)).not.toBeInTheDocument();
  });

  it("falls back to updatedAt for a topic closed without a session", () => {
    mockUsePastTopics.mockReturnValue({
      data: {
        content: [
          {
            id: 2,
            title: "Closed topic",
            kind: "REQUEST",
            status: "DONE",
            voteCount: 0,
            canReopen: false,
            updatedAt: "2026-06-25T12:00:00Z",
            scheduledFor: null,
          },
        ],
        totalPages: 1,
      },
      isLoading: false,
      isError: false,
    } as never);

    render(
      <MemoryRouter>
        <PastTopicsPanel slug="backend-guild" />
      </MemoryRouter>,
    );

    expect(screen.getByText("25")).toBeInTheDocument();
    expect(screen.getByText("Jun")).toBeInTheDocument();
    expect(screen.queryByText(/votes when held/)).not.toBeInTheDocument();
  });

  /** Spec, "Backlog view": closed and dropped items are found under Past. The tab used to hold
   *  them as DONE ACTION topics; the aggregate took them out of `topic` entirely. */
  it("holds the action items the community has settled", () => {
    mockUsePastTopics.mockReturnValue({
      data: { content: [], totalPages: 0 },
      isLoading: false,
      isError: false,
    } as never);
    mockUseSettled.mockReturnValue({
      items: [
        anActionItem({
          id: 9,
          title: "Renew the certificate",
          outcome: "DONE",
        }),
      ],
      total: 1,
      isLoading: false,
      isError: false,
    });

    render(
      <MemoryRouter>
        <PastTopicsPanel slug="backend-guild" />
      </MemoryRouter>,
    );

    expect(screen.getByText("Renew the certificate")).toBeInTheDocument();
  });
});
