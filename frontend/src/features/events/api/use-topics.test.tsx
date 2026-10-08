import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { renderHook, waitFor } from "@testing-library/react";
import type { ReactNode } from "react";
import { beforeEach, describe, expect, it, vi } from "vitest";

import { httpEventsGateway } from "@/features/events/adapters/http-events-gateway";
import { topicKeys } from "@/features/events/api/query-keys";
import {
  useDeleteTopic,
  useEditTopic,
  usePastTopics,
  useProposeTopic,
  useTopics,
  useUnvoteTopic,
  useVoteTopic,
} from "@/features/events/api/use-topics";
import type { Topic } from "@/features/events/domain/topic";

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

function wrapper({ children }: { children: ReactNode }) {
  const client = new QueryClient({
    defaultOptions: { queries: { retry: false } },
  });
  return <QueryClientProvider client={client}>{children}</QueryClientProvider>;
}

beforeEach(() => vi.clearAllMocks());

describe("useTopics", () => {
  it("returns the topic page", async () => {
    // given
    gateway.listTopics.mockResolvedValue({
      content: [],
      totalElements: 0,
      totalPages: 0,
      number: 0,
      size: 20,
    });

    // when
    const { result } = renderHook(
      () => useTopics("backend-guild", { page: 0, size: 20 }),
      {
        wrapper,
      },
    );

    // then
    await waitFor(() => expect(result.current.isSuccess).toBe(true));
  });
});

describe("usePastTopics", () => {
  it("requests the PAST phase with search + page params", async () => {
    gateway.listTopics.mockResolvedValue({
      content: [],
      totalElements: 0,
      totalPages: 0,
      number: 0,
      size: 20,
    });

    const { result } = renderHook(
      () => usePastTopics("backend-guild", { page: 1, size: 20, q: "kafka" }),
      { wrapper },
    );

    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(gateway.listTopics.mock.calls[0]).toEqual([
      "backend-guild",
      { page: 1, size: 20, q: "kafka", phase: "PAST" },
    ]);
  });
});

describe("useVoteTopic / useProposeTopic", () => {
  it("votes through the gateway", async () => {
    // given
    gateway.voteTopic.mockResolvedValue({} as never);
    const { result } = renderHook(() => useVoteTopic("backend-guild"), {
      wrapper,
    });

    // when
    result.current.mutate(5);

    // then
    await waitFor(() => expect(gateway.voteTopic.mock.calls[0]).toEqual([5]));
  });

  it("proposes through the gateway", async () => {
    // given
    gateway.proposeTopic.mockResolvedValue({} as never);
    const { result } = renderHook(() => useProposeTopic("backend-guild"), {
      wrapper,
    });

    // when
    result.current.mutate({ kind: "REQUEST", title: "X" });

    // then
    await waitFor(() =>
      expect(gateway.proposeTopic.mock.calls).toHaveLength(1),
    );
  });
});

describe("useUnvoteTopic", () => {
  it("unvotes through the gateway", async () => {
    gateway.unvoteTopic.mockResolvedValue({} as never);
    const { result } = renderHook(() => useUnvoteTopic("backend-guild"), {
      wrapper,
    });
    result.current.mutate(7);
    await waitFor(() => expect(gateway.unvoteTopic.mock.calls[0]).toEqual([7]));
  });
});

describe("useEditTopic", () => {
  it("edits through the gateway", async () => {
    gateway.editTopic.mockResolvedValue({} as never);
    const { result } = renderHook(() => useEditTopic("backend-guild"), {
      wrapper,
    });
    result.current.mutate({ topicId: 3, input: { kind: "OFFER", title: "Y" } });
    await waitFor(() =>
      expect(gateway.editTopic.mock.calls[0]).toEqual([
        3,
        { kind: "OFFER", title: "Y" },
      ]),
    );
  });
});

describe("useDeleteTopic", () => {
  it("deletes through the gateway", async () => {
    gateway.deleteTopic.mockResolvedValue({} as never);
    const { result } = renderHook(() => useDeleteTopic("backend-guild"), {
      wrapper,
    });
    result.current.mutate(9);
    await waitFor(() => expect(gateway.deleteTopic.mock.calls[0]).toEqual([9]));
  });
});

describe("optimistic vote cache", () => {
  const LIST_KEY = topicKeys.list("backend-guild", {
    page: 0,
    size: 50,
    status: "PROPOSED",
  });

  const sample: Topic = {
    id: 5,
    communityId: 1,
    kind: "REQUEST",
    title: "ES intro",
    description: "",
    tags: [],
    proposerUserId: "user-1",
    proposerDisplayName: "Alice",
    canClose: false,
    canReopen: false,
    voteCount: 2,
    hasVoted: false,
    canEdit: false,
    canDelete: false,
    status: "PROPOSED",
    scheduledFor: null,
    createdAt: "x",
    updatedAt: "x",
  };

  function seededClient(topic: Topic) {
    const client = new QueryClient({
      defaultOptions: { queries: { retry: false } },
    });
    client.setQueryData(LIST_KEY, { content: [topic] });
    return client;
  }

  function clientWrapper(client: QueryClient) {
    return function Wrapper({ children }: { children: ReactNode }) {
      return (
        <QueryClientProvider client={client}>{children}</QueryClientProvider>
      );
    };
  }

  function cachedTopic(client: QueryClient): Topic {
    const page = client.getQueryData<{ content: Topic[] }>(LIST_KEY);
    return page!.content[0]!;
  }

  it("increments the cached count and sets hasVoted immediately on vote", async () => {
    gateway.voteTopic.mockResolvedValue({} as never);
    const client = seededClient(sample);
    const { result } = renderHook(() => useVoteTopic("backend-guild"), {
      wrapper: clientWrapper(client),
    });

    result.current.mutate(5);

    await waitFor(() => expect(cachedTopic(client).voteCount).toBe(3));
    expect(cachedTopic(client).hasVoted).toBe(true);
  });

  it("rolls the optimistic vote back to the snapshot when the request fails", async () => {
    gateway.voteTopic.mockRejectedValue(new Error("boom"));
    const client = seededClient(sample);
    const { result } = renderHook(() => useVoteTopic("backend-guild"), {
      wrapper: clientWrapper(client),
    });

    result.current.mutate(5);

    await waitFor(() => expect(result.current.isError).toBe(true));
    expect(cachedTopic(client).voteCount).toBe(2);
    expect(cachedTopic(client).hasVoted).toBe(false);
  });

  it("decrements the cached count (clamped at zero) and clears hasVoted on unvote", async () => {
    gateway.unvoteTopic.mockResolvedValue(undefined);
    const client = seededClient({ ...sample, voteCount: 0, hasVoted: true });
    const { result } = renderHook(() => useUnvoteTopic("backend-guild"), {
      wrapper: clientWrapper(client),
    });

    result.current.mutate(5);

    await waitFor(() => expect(cachedTopic(client).hasVoted).toBe(false));
    expect(cachedTopic(client).voteCount).toBe(0);
  });
});
