import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { renderHook, waitFor } from "@testing-library/react";
import type { ReactNode } from "react";
import { beforeEach, describe, expect, it, vi } from "vitest";

import { httpEventsGateway } from "@/features/events/adapters/http-events-gateway";
import { topicKeys } from "@/features/events/api/query-keys";
import {
  usePresentFromBacklog,
  useQueueTopic,
  useReturnToBacklog,
} from "@/features/events/api/use-backlog-pull";

vi.mock("@/features/events/adapters/http-events-gateway", () => ({
  httpEventsGateway: {
    addSlot: vi.fn(),
    queueTopic: vi.fn(),
    returnToBacklog: vi.fn(),
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

describe("usePresentFromBacklog", () => {
  it("adds a topic-backed slot with self as presenter", async () => {
    gateway.addSlot.mockResolvedValue({} as never);
    const { result } = renderHook(
      () => usePresentFromBacklog("backend-guild", 9, 3),
      { wrapper },
    );
    result.current.mutate({
      title: "Flaky E2E",
      topicId: 99,
      durationMinutes: 20,
    });
    await waitFor(() =>
      expect(gateway.addSlot.mock.calls[0]).toEqual([
        9,
        3,
        { title: "Flaky E2E", topicId: 99, durationMinutes: 20 },
      ]),
    );
  });
});

describe("useQueueTopic", () => {
  it("queues a topic as an open slot", async () => {
    gateway.queueTopic.mockResolvedValue({} as never);
    const { result } = renderHook(() => useQueueTopic("backend-guild", 9, 3), {
      wrapper,
    });
    result.current.mutate({ topicId: 99, durationMinutes: 20 });
    await waitFor(() =>
      expect(gateway.queueTopic.mock.calls[0]).toEqual([
        9,
        3,
        { topicId: 99, durationMinutes: 20 },
      ]),
    );
  });
});

describe("a refused pull", () => {
  it("still refreshes the topics, so a topic scheduled meanwhile leaves the picker", async () => {
    gateway.queueTopic.mockRejectedValue(new Error("NOT_PROPOSED"));
    const client = new QueryClient({
      defaultOptions: { queries: { retry: false } },
    });
    const invalidate = vi.spyOn(client, "invalidateQueries");
    const { result } = renderHook(() => useQueueTopic("backend-guild", 9, 3), {
      wrapper: ({ children }: { children: ReactNode }) => (
        <QueryClientProvider client={client}>{children}</QueryClientProvider>
      ),
    });
    result.current.mutate({ topicId: 99, durationMinutes: 20 });
    await waitFor(() => expect(result.current.isError).toBe(true));
    expect(invalidate).toHaveBeenCalledWith({
      queryKey: topicKeys.community("backend-guild"),
    });
  });
});

describe("useReturnToBacklog", () => {
  it("returns a scheduled topic to the backlog by topic id", async () => {
    gateway.returnToBacklog.mockResolvedValue(undefined);
    const { result } = renderHook(() => useReturnToBacklog("backend-guild"), {
      wrapper,
    });
    result.current.mutate(99);
    await waitFor(() =>
      expect(gateway.returnToBacklog.mock.calls[0]).toEqual([99]),
    );
  });
});
