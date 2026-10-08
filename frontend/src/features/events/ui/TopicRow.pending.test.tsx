import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import type { ReactNode } from "react";
import { beforeEach, describe, expect, it, vi } from "vitest";

import { httpEventsGateway } from "@/features/events/adapters/http-events-gateway";
import type { Topic } from "@/features/events/domain/topic";
import { BacklogPanel } from "@/features/events/ui/BacklogPanel";

vi.mock("framer-motion", () => ({
  AnimatePresence: ({ children }: { children: ReactNode }) => <>{children}</>,
  LayoutGroup: ({ children }: { children: ReactNode }) => <>{children}</>,
  motion: { li: (props: { children: ReactNode }) => <li>{props.children}</li> },
}));

vi.mock("@/features/events/adapters/http-events-gateway", () => ({
  httpEventsGateway: {
    listTopics: vi.fn(),
    proposeTopic: vi.fn(),
    voteTopic: vi.fn(),
    unvoteTopic: vi.fn(),
    editTopic: vi.fn(),
    deleteTopic: vi.fn(),
  },
}));

const gateway = vi.mocked(httpEventsGateway);

function topic(id: number, hasVoted: boolean, voteCount: number): Topic {
  return {
    id,
    communityId: 1,
    kind: "REQUEST",
    title: `Topic ${id}`,
    description: "",
    tags: [],
    proposerUserId: "user-1",
    proposerDisplayName: "Alice",
    canClose: false,
    canReopen: false,
    voteCount,
    hasVoted,
    canEdit: false,
    canDelete: false,
    status: "PROPOSED",
    scheduledFor: null,
    createdAt: "x",
    updatedAt: "x",
  };
}

describe("vote button pending state (derived from the mutation cache)", () => {
  beforeEach(() => vi.clearAllMocks());

  it("re-enables the vote button once the POST settles, even if the refetch is still in flight", async () => {
    // initial list load resolves once; every later refetch hangs forever
    // (mirrors a refetch cancelled/stalled by overlapping vote churn).
    let firstLoad = true;
    gateway.listTopics.mockImplementation(() => {
      if (firstLoad) {
        firstLoad = false;
        return Promise.resolve({
          content: [topic(5, false, 0)],
          totalElements: 1,
          totalPages: 1,
          number: 0,
          size: 50,
        } as never);
      }
      return new Promise(() => {}); // never resolves
    });
    gateway.voteTopic.mockResolvedValue(topic(5, true, 1));

    const client = new QueryClient({
      defaultOptions: {
        queries: { staleTime: 30_000, retry: 1, refetchOnWindowFocus: false },
      },
    });
    render(
      <QueryClientProvider client={client}>
        <BacklogPanel slug="backend-guild" />
      </QueryClientProvider>,
    );

    const voteBtn = await screen.findByRole<HTMLButtonElement>("button", {
      name: /Upvote Topic 5/i,
    });
    expect(voteBtn.disabled).toBe(false);

    fireEvent.click(voteBtn);

    // The POST resolves quickly; the optimistic update flips the label.
    // The background refetch is still in flight — but the button must NOT
    // stay disabled because of it.
    await waitFor(() => expect(gateway.voteTopic.mock.calls[0]).toEqual([5]));

    await waitFor(() => {
      const btn = screen
        .getAllByRole<HTMLButtonElement>("button")
        .find((b) => /Topic 5/.test(b.getAttribute("aria-label") ?? ""))!;
      expect(btn.disabled).toBe(false);
    });
  });

  it("only disables the row whose vote is in flight, not its siblings", async () => {
    gateway.listTopics.mockResolvedValue({
      content: [topic(5, false, 0), topic(6, false, 0)],
      totalElements: 2,
      totalPages: 1,
      number: 0,
      size: 50,
    });
    // keep the vote request in flight so the pending state is observable
    let resolveVote: (t: Topic) => void = () => {};
    gateway.voteTopic.mockImplementation(
      () => new Promise<Topic>((res) => (resolveVote = res)),
    );

    const client = new QueryClient({
      defaultOptions: { queries: { retry: false } },
    });
    render(
      <QueryClientProvider client={client}>
        <BacklogPanel slug="backend-guild" />
      </QueryClientProvider>,
    );

    const btnFor = (id: number) =>
      screen
        .getAllByRole<HTMLButtonElement>("button")
        .find((b) =>
          (b.getAttribute("aria-label") ?? "").includes(`Topic ${id}`),
        )!;

    fireEvent.click(
      await screen.findByRole("button", { name: /Upvote Topic 5/i }),
    );

    // Topic 5 is in flight -> disabled; Topic 6 is untouched -> still clickable.
    await waitFor(() => expect(btnFor(5).disabled).toBe(true));
    expect(btnFor(6).disabled).toBe(false);

    resolveVote(topic(5, true, 1));
    await waitFor(() => expect(btnFor(5).disabled).toBe(false));
  });
});
